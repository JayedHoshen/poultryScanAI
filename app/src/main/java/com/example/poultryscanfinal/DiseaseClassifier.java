package com.example.poultryscanfinal;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * On-device poultry disease classifier backed by poultry_disease_model.tflite.
 *
 * VERIFIED MODEL FACTS - read directly out of the supplied .tflite flatbuffer,
 * not assumed:
 *
 *   input  : "serving_default_keras_tensor_548:0"  shape [1, 224, 224, 3]
 *            dtype FLOAT32, no quantization parameters
 *   output : "StatefulPartitionedCall_1:0"         shape [1, 4]
 *            dtype FLOAT32, produced by a SOFTMAX op, so it is already a
 *            probability distribution that sums to 1
 *   graph  : EfficientNetV2-B0 backbone. Its first three ops are
 *              MUL by 0.003921569            (= 1/255, Keras Rescaling layer)
 *              SUB [0.485, 0.456, 0.406]     (ImageNet mean)
 *              MUL [4.3668, 4.4643, 4.4444]  (= 1 / [0.229, 0.224, 0.225])
 *
 * Because rescaling AND ImageNet normalisation are baked into the graph, the
 * interpreter must be fed RAW pixel values in the 0..255 range as float32.
 * Dividing by 255 in Java would normalise twice and wreck the prediction.
 *
 * Shapes and dtypes are still read back from the Interpreter at runtime and any
 * mismatch fails loudly, so swapping in a different model produces a clear
 * error instead of silent nonsense.
 */
public class DiseaseClassifier {

    private static final String MODEL_FILE = "poultry_disease_model.tflite";
    private static final String LABEL_FILE = "labels.txt";

    /** Smallest source image we are willing to feed the model. */
    private static final int MIN_SOURCE_DIMENSION = 32;

    /**
     * Preprocessing contract, derived from the graph inspection documented above.
     * If a future model is ever exported WITHOUT the Rescaling layer, flip this
     * flag instead of editing the pixel loop.
     */
    private static final boolean MODEL_EXPECTS_0_TO_1_INPUT = false;

    /** Thrown for every recoverable failure so callers never see raw TFLite errors. */
    public static class ClassifierException extends Exception {
        public ClassifierException(String message) {
            super(message);
        }

        public ClassifierException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Holds the classification outcome for a single image. */
    public static class Result {
        /** Raw label from labels.txt, e.g. "ncd". */
        public final String label;
        /** Probability of {@link #label}, 0.0 - 1.0. */
        public final float confidence;
        /** Full probability distribution, in labels.txt order. */
        public final float[] allScores;
        /** Labels in model output order, so scores can be attributed. */
        public final List<String> labels;

        Result(String label, float confidence, float[] allScores, List<String> labels) {
            this.label = label;
            this.confidence = confidence;
            this.allScores = allScores;
            this.labels = labels;
        }
    }

    private final Interpreter interpreter;
    private final List<String> labels;

    private final int inputWidth;
    private final int inputHeight;
    private final int inputChannels;
    private final DataType inputType;
    private final float inputQuantScale;
    private final int inputQuantZeroPoint;

    private final int numClasses;
    private final DataType outputType;
    private final float outputQuantScale;
    private final int outputQuantZeroPoint;

    /** Reusable input buffer, so each scan does not allocate 600 KB again. */
    private final ByteBuffer inputBuffer;
    private final int[] pixelCache;

    private boolean closed;

    public DiseaseClassifier(Context context) throws ClassifierException {
        MappedByteBuffer modelBuffer = loadModelFile(context);
        this.labels = loadLabels(context);

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

        try {
            this.interpreter = new Interpreter(modelBuffer, options);
        } catch (Exception e) {
            // Most likely real-world cause: the bundled TFLite runtime is older
            // than the operator versions inside the model. See build.gradle.kts.
            throw new ClassifierException("The AI model could not be initialised on this device.", e);
        }

        Tensor input;
        Tensor output;
        try {
            input = interpreter.getInputTensor(0);
            output = interpreter.getOutputTensor(0);
        } catch (Exception e) {
            interpreter.close();
            throw new ClassifierException("The AI model has an unexpected structure.", e);
        }

        int[] inputShape = input.shape();
        if (inputShape.length != 4 || inputShape[0] != 1) {
            interpreter.close();
            throw new ClassifierException("Unsupported model input shape "
                    + Arrays.toString(inputShape) + "; expected [1, height, width, channels].");
        }
        this.inputHeight = inputShape[1];
        this.inputWidth = inputShape[2];
        this.inputChannels = inputShape[3];
        if (inputChannels != 3) {
            interpreter.close();
            throw new ClassifierException("Unsupported model input: " + inputChannels
                    + " channels, expected 3 (RGB).");
        }

        this.inputType = input.dataType();
        if (inputType != DataType.FLOAT32 && inputType != DataType.UINT8 && inputType != DataType.INT8) {
            interpreter.close();
            throw new ClassifierException("Unsupported model input data type: " + inputType);
        }
        this.inputQuantScale = input.quantizationParams().getScale();
        this.inputQuantZeroPoint = input.quantizationParams().getZeroPoint();

        int[] outputShape = output.shape();
        if (outputShape.length != 2 || outputShape[0] != 1) {
            interpreter.close();
            throw new ClassifierException("Unsupported model output shape "
                    + Arrays.toString(outputShape) + "; expected [1, numClasses].");
        }
        this.numClasses = outputShape[1];
        this.outputType = output.dataType();
        if (outputType != DataType.FLOAT32 && outputType != DataType.UINT8 && outputType != DataType.INT8) {
            interpreter.close();
            throw new ClassifierException("Unsupported model output data type: " + outputType);
        }
        this.outputQuantScale = output.quantizationParams().getScale();
        this.outputQuantZeroPoint = output.quantizationParams().getZeroPoint();

        if (labels.size() != numClasses) {
            interpreter.close();
            throw new ClassifierException("labels.txt lists " + labels.size()
                    + " labels but the model outputs " + numClasses + " classes.");
        }

        int bytesPerChannel = (inputType == DataType.FLOAT32) ? 4 : 1;
        this.inputBuffer = ByteBuffer
                .allocateDirect(bytesPerChannel * inputWidth * inputHeight * inputChannels)
                .order(ByteOrder.nativeOrder());
        this.pixelCache = new int[inputWidth * inputHeight];
    }

    // --------------------------------------------------------------- loading

    private MappedByteBuffer loadModelFile(Context context) throws ClassifierException {
        AssetFileDescriptor descriptor = null;
        FileInputStream stream = null;
        try {
            descriptor = context.getAssets().openFd(MODEL_FILE);
            long declaredLength = descriptor.getDeclaredLength();
            if (declaredLength <= 0) {
                throw new ClassifierException("The AI model file in assets is empty or corrupted.");
            }
            stream = new FileInputStream(descriptor.getFileDescriptor());
            FileChannel channel = stream.getChannel();
            return channel.map(FileChannel.MapMode.READ_ONLY, descriptor.getStartOffset(), declaredLength);
        } catch (ClassifierException e) {
            throw e;
        } catch (IOException e) {
            // openFd() also fails when the asset got compressed into the APK,
            // which androidResources { noCompress += "tflite" } prevents.
            throw new ClassifierException("The AI model file (" + MODEL_FILE
                    + ") is missing or could not be opened.", e);
        } finally {
            closeQuietly(stream);
            closeQuietly(descriptor);
        }
    }

    private List<String> loadLabels(Context context) throws ClassifierException {
        List<String> labelList = new ArrayList<>();
        InputStream is = null;
        BufferedReader reader = null;
        try {
            is = context.getAssets().open(LABEL_FILE);
            reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    labelList.add(trimmed);
                }
            }
        } catch (IOException e) {
            throw new ClassifierException("The label file (" + LABEL_FILE
                    + ") is missing or unreadable.", e);
        } finally {
            closeQuietly(reader);
            closeQuietly(is);
        }
        if (labelList.isEmpty()) {
            throw new ClassifierException("The label file (" + LABEL_FILE + ") is empty.");
        }
        return Collections.unmodifiableList(labelList);
    }

    // ------------------------------------------------------------- inference

    /** Runs inference on a single bitmap. Must be called from a background thread. */
    public synchronized Result classify(Bitmap bitmap) throws ClassifierException {
        if (closed) {
            throw new ClassifierException("The AI model has already been released.");
        }
        if (bitmap == null || bitmap.isRecycled()) {
            throw new ClassifierException("The selected image could not be read.");
        }
        if (bitmap.getWidth() < MIN_SOURCE_DIMENSION || bitmap.getHeight() < MIN_SOURCE_DIMENSION) {
            throw new ClassifierException("This image is too small to analyse ("
                    + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + " pixels). Please use a larger photo.");
        }

        Bitmap prepared = null;
        try {
            prepared = prepareBitmap(bitmap);
            fillInputBuffer(prepared);

            Object outputContainer = createOutputContainer();
            try {
                interpreter.run(inputBuffer, outputContainer);
            } catch (Exception e) {
                throw new ClassifierException("The AI model failed while analysing this image.", e);
            }

            float[] scores = readScores(outputContainer);
            if (!looksLikeProbabilities(scores)) {
                // Defensive only: this model already ends in SOFTMAX. A re-export
                // without it would otherwise produce meaningless "confidence".
                softmaxInPlace(scores);
            }

            int bestIndex = 0;
            for (int i = 1; i < scores.length; i++) {
                if (scores[i] > scores[bestIndex]) {
                    bestIndex = i;
                }
            }
            return new Result(labels.get(bestIndex), scores[bestIndex], scores, labels);
        } finally {
            // prepareBitmap only returns the caller's bitmap when it was already
            // exactly the right size and config, so this never recycles theirs.
            if (prepared != null && prepared != bitmap && !prepared.isRecycled()) {
                prepared.recycle();
            }
        }
    }

    /** Converts to ARGB_8888 and resizes to the model input size. */
    private Bitmap prepareBitmap(Bitmap source) throws ClassifierException {
        Bitmap argb = null;
        try {
            if (source.getConfig() == Bitmap.Config.ARGB_8888) {
                argb = source;
            } else {
                // Also covers HARDWARE bitmaps, whose pixels cannot be read directly.
                argb = source.copy(Bitmap.Config.ARGB_8888, false);
                if (argb == null) {
                    throw new ClassifierException("The selected image format is not supported.");
                }
            }

            Bitmap scaled = Bitmap.createScaledBitmap(argb, inputWidth, inputHeight, true);
            if (scaled == null) {
                throw new ClassifierException("The selected image could not be resized for analysis.");
            }
            // createScaledBitmap returns the input itself when the size already matches.
            if (scaled != argb && argb != source) {
                argb.recycle();
            }
            return scaled;
        } catch (OutOfMemoryError e) {
            if (argb != null && argb != source && !argb.isRecycled()) {
                argb.recycle();
            }
            throw new ClassifierException("Not enough memory to process this image.", e);
        } catch (IllegalArgumentException e) {
            if (argb != null && argb != source && !argb.isRecycled()) {
                argb.recycle();
            }
            throw new ClassifierException("The selected image could not be processed.", e);
        }
    }

    private void fillInputBuffer(Bitmap bitmap) {
        inputBuffer.rewind();
        bitmap.getPixels(pixelCache, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        for (int pixel : pixelCache) {
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            if (inputType == DataType.FLOAT32) {
                if (MODEL_EXPECTS_0_TO_1_INPUT) {
                    inputBuffer.putFloat(r / 255.0f);
                    inputBuffer.putFloat(g / 255.0f);
                    inputBuffer.putFloat(b / 255.0f);
                } else {
                    // Raw 0-255. The model's own Rescaling + Normalization layers
                    // do the rest, so do NOT divide here.
                    inputBuffer.putFloat(r);
                    inputBuffer.putFloat(g);
                    inputBuffer.putFloat(b);
                }
            } else {
                inputBuffer.put(quantizePixel(r));
                inputBuffer.put(quantizePixel(g));
                inputBuffer.put(quantizePixel(b));
            }
        }
        inputBuffer.rewind();
    }

    /** Only reached if a quantized-input model is ever dropped in; unused today. */
    private byte quantizePixel(int channelValue) {
        if (inputQuantScale == 0f) {
            return (byte) channelValue;
        }
        int q = Math.round(channelValue / inputQuantScale) + inputQuantZeroPoint;
        if (inputType == DataType.UINT8) {
            q = Math.max(0, Math.min(255, q));
        } else {
            q = Math.max(-128, Math.min(127, q));
        }
        return (byte) q;
    }

    private Object createOutputContainer() {
        if (outputType == DataType.FLOAT32) {
            return new float[1][numClasses];
        }
        return new byte[1][numClasses];
    }

    private float[] readScores(Object container) {
        if (outputType == DataType.FLOAT32) {
            return ((float[][]) container)[0];
        }
        byte[] raw = ((byte[][]) container)[0];
        float[] scores = new float[numClasses];
        float scale = (outputQuantScale == 0f) ? 1f / 255f : outputQuantScale;
        for (int i = 0; i < numClasses; i++) {
            int value = (outputType == DataType.UINT8) ? (raw[i] & 0xFF) : raw[i];
            scores[i] = (value - outputQuantZeroPoint) * scale;
        }
        return scores;
    }

    private static boolean looksLikeProbabilities(float[] scores) {
        float sum = 0f;
        for (float score : scores) {
            if (Float.isNaN(score) || score < -0.001f || score > 1.001f) {
                return false;
            }
            sum += score;
        }
        return Math.abs(sum - 1f) < 0.05f;
    }

    private static void softmaxInPlace(float[] scores) {
        float max = Float.NEGATIVE_INFINITY;
        for (float score : scores) {
            if (score > max) {
                max = score;
            }
        }
        float sum = 0f;
        for (int i = 0; i < scores.length; i++) {
            scores[i] = (float) Math.exp(scores[i] - max);
            sum += scores[i];
        }
        if (sum <= 0f || Float.isNaN(sum)) {
            Arrays.fill(scores, 1f / scores.length);
            return;
        }
        for (int i = 0; i < scores.length; i++) {
            scores[i] /= sum;
        }
    }

    // ------------------------------------------------------------------ misc

    public List<String> getLabels() {
        return labels;
    }

    public int getInputWidth() {
        return inputWidth;
    }

    public int getInputHeight() {
        return inputHeight;
    }

    /** Human-readable tensor summary, useful when debugging a model swap. */
    public String describeModel() {
        return "input " + inputWidth + "x" + inputHeight + "x" + inputChannels + " " + inputType
                + ", output [1," + numClasses + "] " + outputType;
    }

    public synchronized void close() {
        if (!closed) {
            closed = true;
            interpreter.close();
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Nothing useful to do while cleaning up.
            }
        }
    }
}
