# poultry_disease_model.tflite — verified facts and known limitations

Everything below was read directly out of the supplied `.tflite` flatbuffer
(tensor table, operator table, constant buffers). Nothing here is assumed.

## Tensors

| | |
|---|---|
| Input tensor | `serving_default_keras_tensor_548:0` |
| Input shape | `[1, 224, 224, 3]` (NHWC) |
| Input dtype | `FLOAT32` |
| Input quantization | none (scale 0, zero point 0) |
| Output tensor | `StatefulPartitionedCall_1:0` |
| Output shape | `[1, 4]` |
| Output dtype | `FLOAT32` |
| Output semantics | probabilities — the last op in the graph is `SOFTMAX` |

## Preprocessing (this is the important one)

The first three operators of the graph are, in order:

1. `MUL` by the scalar `0.003921569` → this is `1/255`, the Keras `Rescaling` layer
2. `SUB` by `[0.485, 0.456, 0.406]` → ImageNet channel means
3. `MUL` by `[4.36681, 4.46429, 4.44444]` → `1 / [0.229, 0.224, 0.225]`, ImageNet stds

Rescaling **and** ImageNet normalisation are therefore baked into the model.
The interpreter must be fed **raw 0–255 float32 RGB**. Dividing by 255 in Java
would normalise twice and destroy the prediction.

The original `DiseaseClassifier` comment was correct on this point, and the
behaviour has been kept.

## Architecture

EfficientNetV2-B0 backbone (`CONV_2D` ×75, `DEPTHWISE_CONV_2D` ×16, SiLU
activations as `LOGISTIC`+`MUL`), global average pooling, then `Dense(128, relu)`
→ `Dense(4)` → `SOFTMAX`.

## Runtime version requirement

The model carries `min_runtime_version = 2.17.0` and uses **`FULLY_CONNECTED`
version 12** with per-channel int8 weights (dynamic-range quantisation — that is
why the file is 6.7 MB rather than ~28 MB).

TensorFlow Lite **2.14.0**, which the project previously depended on, cannot
parse operator version 12. It fails at `new Interpreter(...)` with:

```
Didn't find op for builtin opcode 'FULLY_CONNECTED' version '12'
Registration failed.
```

`app/build.gradle.kts` therefore now uses `org.tensorflow:tensorflow-lite:2.17.0`.
If that coordinate cannot be resolved in your environment, the alternatives are,
in order of preference:

1. a newer TFLite / LiteRT runtime artifact that is at least 2.17.0
2. re-exporting the model from Keras with `converter.target_spec` set so that no
   per-channel hybrid `FULLY_CONNECTED` is emitted (i.e. plain float32 export)

Do not downgrade the runtime below 2.17.0 and expect the model to load.

## Class order

`labels.txt` is the single runtime source of truth and matches `label_map.json`:

```
0 cocci    → Coccidiosis
1 healthy  → Healthy
2 ncd      → Newcastle Disease
3 salmo    → Salmonellosis
```

`label_map.json` is **not** bundled into the APK and is not parsed at runtime.
`DiseaseClassifier` asserts at construction time that the number of labels equals
the model's output class count and refuses to run otherwise.

## Known limitations — please do not hide these

* **No accuracy figures.** No validation set, confusion matrix or held-out metrics
  were supplied with the model. The app therefore makes no accuracy claim
  anywhere, and the 0.60 confidence threshold in `ResultActivity` is a
  conservative default, **not** a validated operating point. If you ever evaluate
  the model properly, set the threshold from that evaluation.
* **No poultry detector.** The model has exactly four output classes and no
  "not poultry" or "unknown" class. Shown a photo of a chair, a person or a
  landscape, it will still distribute probability across the four classes and can
  do so confidently. Nothing in the app can detect this, because nothing in the
  model can. The low-confidence path catches some of these, not all.
* **Single-image screening.** One photo of droppings or a bird is not a flock
  diagnosis. Co-infections, early-stage disease and non-visual signs are outside
  what an image classifier can see.
* **Training-data domain is unknown.** Lighting, camera, background and sample
  presentation in the field may differ from whatever the model was trained on,
  and performance under that shift is unmeasured.

Because of all of the above, every result is presented as an AI screening result
with a visible disclaimer, and no medication or dosage is ever suggested.
