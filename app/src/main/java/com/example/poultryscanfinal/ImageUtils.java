package com.example.poultryscanfinal;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Decodes user-selected images safely.
 *
 * Camera photos are often 12 MP or larger. Loading one at full resolution is a
 * reliable way to hit OutOfMemoryError, so every decode here is downsampled to a
 * sensible maximum edge length. EXIF rotation is applied as well, otherwise a
 * portrait phone photo reaches the model lying on its side.
 */
public final class ImageUtils {

    /** Comfortably above the 224 px the model needs, small enough to stay cheap. */
    public static final int DEFAULT_MAX_DIMENSION = 1024;

    private ImageUtils() {
    }

    public static Bitmap decodeBitmap(ContentResolver resolver, Uri uri, int maxDimension)
            throws IOException {
        if (resolver == null || uri == null) {
            throw new IOException("No image was provided.");
        }

        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF orientation itself.
            ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
            bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                // Software allocator: hardware bitmaps cannot be read with getPixels().
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setMutableRequired(false);
                int longestEdge = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                int sampleSize = calculateSampleSize(longestEdge, maxDimension);
                if (sampleSize > 1) {
                    decoder.setTargetSampleSize(sampleSize);
                }
            });
        } else {
            bitmap = decodeWithBitmapFactory(resolver, uri, maxDimension);
            bitmap = applyExifRotation(resolver, uri, bitmap);
        }

        if (bitmap == null) {
            throw new IOException("The image could not be decoded.");
        }
        return bitmap;
    }

    private static Bitmap decodeWithBitmapFactory(ContentResolver resolver, Uri uri, int maxDimension)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream boundsStream = null;
        try {
            boundsStream = resolver.openInputStream(uri);
            if (boundsStream == null) {
                throw new IOException("The image could not be opened.");
            }
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        } finally {
            closeQuietly(boundsStream);
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("The image could not be decoded.");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = calculateSampleSize(
                Math.max(bounds.outWidth, bounds.outHeight), maxDimension);

        InputStream pixelStream = null;
        try {
            pixelStream = resolver.openInputStream(uri);
            if (pixelStream == null) {
                throw new IOException("The image could not be opened.");
            }
            return BitmapFactory.decodeStream(pixelStream, null, options);
        } catch (OutOfMemoryError e) {
            throw new IOException("Not enough memory to open this image.");
        } finally {
            closeQuietly(pixelStream);
        }
    }

    private static Bitmap applyExifRotation(ContentResolver resolver, Uri uri, Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int degrees = 0;
        InputStream stream = null;
        try {
            stream = resolver.openInputStream(uri);
            if (stream == null) {
                return bitmap;
            }
            ExifInterface exif = new ExifInterface(stream);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                degrees = 90;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                degrees = 180;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                degrees = 270;
            }
        } catch (IOException | RuntimeException e) {
            return bitmap; // Orientation is a nicety; never fail the scan over it.
        } finally {
            closeQuietly(stream);
        }

        if (degrees == 0) {
            return bitmap;
        }
        try {
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (OutOfMemoryError | IllegalArgumentException e) {
            return bitmap;
        }
    }

    private static int calculateSampleSize(int longestEdge, int maxDimension) {
        int sampleSize = 1;
        while (maxDimension > 0 && (longestEdge / sampleSize) > maxDimension) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private static void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Nothing useful to do while cleaning up.
            }
        }
    }

    // ------------------------------------------------------- scan image store

    /** Folder inside the app's private storage where scan copies live. */
    private static final String SCAN_DIR = "scans";
    private static final int SCAN_MAX_DIMENSION = 1024;
    private static final int SCAN_JPEG_QUALITY = 85;

    /**
     * Copies the scanned image into app-private internal storage as a compressed
     * JPEG and returns its absolute path. Only this path is stored in Room.
     *
     * @return the absolute file path, never null
     * @throws IOException if the image cannot be read or written
     */
    public static String saveScanImage(Context context, Uri sourceUri) throws IOException {
        if (context == null || sourceUri == null) {
            throw new IOException("No image to save.");
        }

        File directory = new File(context.getFilesDir(), SCAN_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create the scan image folder.");
        }

        Bitmap bitmap = decodeBitmap(context.getContentResolver(), sourceUri, SCAN_MAX_DIMENSION);
        File target = new File(directory, "scan_" + System.currentTimeMillis() + ".jpg");
        OutputStream out = null;
        try {
            out = new FileOutputStream(target);
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, SCAN_JPEG_QUALITY, out)) {
                throw new IOException("The scan image could not be compressed.");
            }
            out.flush();
        } catch (IOException e) {
            if (target.exists()) {
                target.delete();
            }
            throw e;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Nothing useful to do while cleaning up.
                }
            }
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        return target.getAbsolutePath();
    }

    /** Best-effort removal of a stored scan image. Never throws. */
    public static void deleteScanImage(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        try {
            File file = new File(path);
            if (file.exists()) {
                file.delete();
            }
        } catch (SecurityException ignored) {
            // A leftover file is harmless; never crash over cleanup.
        }
    }

    /** True when a stored scan image still exists on disk. */
    public static boolean scanImageExists(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.length() > 0;
    }

    /**
     * Decodes a previously stored scan image. Returns null when the file is
     * missing or unreadable, so history items degrade gracefully instead of
     * crashing.
     */
    public static Bitmap decodeStoredScanImage(String path, int maxDimension) {
        if (!scanImageExists(path)) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = calculateSampleSize(
                    Math.max(bounds.outWidth, bounds.outHeight), maxDimension);
            return BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }
}
