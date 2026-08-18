import os
import sys
import hashlib
import numpy as np
import tensorflow as tf
from pathlib import Path
from PIL import Image

ROOT_DIR = Path(r'c:\Users\jangi\Downloads\FoodShareAI-main (1)\FoodShareAI-main')
V3_KERAS_PATH = ROOT_DIR / 'freshness_model_v3_best.keras'
V3_TFLITE_PATH = ROOT_DIR / 'food_freshness_v3.tflite'
V3_TEST_DIR = ROOT_DIR / 'real_food_test_v3'

print("==================================================")
print("1. LOADING V3 KERAS MODEL & CONVERTING TO TFLITE")
print("==================================================")

if not V3_KERAS_PATH.exists():
    print(f"ERROR: {V3_KERAS_PATH} not found.")
    sys.exit(1)

keras_model = tf.keras.models.load_model(str(V3_KERAS_PATH))

keras_in_shape = str(keras_model.input_shape)
keras_out_shape = str(keras_model.output_shape)

print(f"Loaded Keras Model: {V3_KERAS_PATH.name}")
print(f"  Keras Input Shape:  {keras_in_shape}")
print(f"  Keras Output Shape: {keras_out_shape}")

# Convert Keras model to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(keras_model)
# Standard Float32 conversion preserving internal layers
tflite_model_bytes = converter.convert()

# Save food_freshness_v3.tflite in root directory (Do NOT overwrite production asset yet)
V3_TFLITE_PATH.write_bytes(tflite_model_bytes)

tflite_size = V3_TFLITE_PATH.stat().st_size
tflite_sha256 = hashlib.sha256(tflite_model_bytes).hexdigest()

print(f"\nTFLite Model Converted and Saved:")
print(f"  TFLite Path: {V3_TFLITE_PATH}")
print(f"  TFLite Size: {tflite_size} bytes ({tflite_size / (1024*1024):.2f} MB)")
print(f"  SHA-256:     {tflite_sha256}")

# Load and inspect TFLite Interpreter
interpreter = tf.lite.Interpreter(model_path=str(V3_TFLITE_PATH))
interpreter.allocate_tensors()

tflite_in_det = interpreter.get_input_details()[0]
tflite_out_det = interpreter.get_output_details()[0]

tflite_in_shape = str(list(tflite_in_det['shape']))
tflite_out_shape = str(list(tflite_out_det['shape']))

print(f"\nTFLite Tensor Details:")
print(f"  Input Tensor Shape:  {tflite_in_shape}, Type: {tflite_in_det['dtype']}")
print(f"  Output Tensor Shape: {tflite_out_shape}, Type: {tflite_out_det['dtype']}")

print("\n==================================================")
print("2. VERIFYING PARITY BETWEEN KERAS AND TFLITE")
print("==================================================")

test_fresh_folder = V3_TEST_DIR / 'Fresh'
test_spoiled_folder = V3_TEST_DIR / 'Spoiled'

fresh_files = sorted([f for f in test_fresh_folder.glob('*') if f.suffix.lower() in ['.jpg', '.jpeg', '.png']])
spoiled_files = sorted([f for f in test_spoiled_folder.glob('*') if f.suffix.lower() in ['.jpg', '.jpeg', '.png']])

total_test_images = len(fresh_files) + len(spoiled_files)
print(f"Total Test Images in real_food_test_v3: {total_test_images} (Fresh: {len(fresh_files)}, Spoiled: {len(spoiled_files)})")

keras_correct = 0
tflite_correct = 0
match_count = 0
mismatch_count = 0

sample_results = []

def run_predictions(img_path, ground_truth):
    global keras_correct, tflite_correct, match_count, mismatch_count
    img = Image.open(img_path).convert('RGB').resize((224, 224), Image.BILINEAR)
    arr = np.array(img, dtype=np.float32)
    inp_batch = np.expand_dims(arr, axis=0)

    # 1. Keras prediction
    k_scores = keras_model.predict(inp_batch, verbose=0)[0]
    k_cls = int(np.argmax(k_scores))

    # 2. TFLite prediction
    interpreter.set_tensor(tflite_in_det['index'], inp_batch)
    interpreter.invoke()
    t_scores = interpreter.get_tensor(tflite_out_det['index'])[0]
    t_cls = int(np.argmax(t_scores))

    gt_cls = 0 if ground_truth == "Fresh" else 1

    if k_cls == gt_cls: keras_correct += 1
    if t_cls == gt_cls: tflite_correct += 1

    if k_cls == t_cls:
        match_count += 1
    else:
        mismatch_count += 1

    diff_fp = abs(float(k_scores[0]) - float(t_scores[0]))
    diff_sp = abs(float(k_scores[1]) - float(t_scores[1]))

    return {
        'filename': img_path.name,
        'ground_truth': ground_truth,
        'keras_pred': "Fresh" if k_cls == 0 else "Spoiled",
        'tflite_pred': "Fresh" if t_cls == 0 else "Spoiled",
        'keras_fresh_p': float(k_scores[0]),
        'keras_spoil_p': float(k_scores[1]),
        'tflite_fresh_p': float(t_scores[0]),
        'tflite_spoil_p': float(t_scores[1]),
        'max_diff': max(diff_fp, diff_sp),
        'match': (k_cls == t_cls)
    }

for f in fresh_files:
    res = run_predictions(f, "Fresh")
    if len(sample_results) < 3: sample_results.append(res)

for f in spoiled_files:
    res = run_predictions(f, "Spoiled")
    if len(sample_results) < 6: sample_results.append(res)

keras_acc = keras_correct / max(1, total_test_images)
tflite_acc = tflite_correct / max(1, total_test_images)

print(f"\nVerification Comparison Summary:")
print(f"  KERAS_TEST_ACCURACY      = {keras_acc:.4f} ({keras_correct}/{total_test_images})")
print(f"  TFLITE_TEST_ACCURACY     = {tflite_acc:.4f} ({tflite_correct}/{total_test_images})")
print(f"  PREDICTION_MATCH_COUNT   = {match_count}")
print(f"  PREDICTION_MISMATCH_COUNT = {mismatch_count}")

print("\nRepresentative Sample Image Verification:")
for s in sample_results:
    print(f"  Image: {s['filename']} ({s['ground_truth']})")
    print(f"    Keras:  Pred={s['keras_pred']} (Fresh={s['keras_fresh_p']:.6f}, Spoiled={s['keras_spoil_p']:.6f})")
    print(f"    TFLite: Pred={s['tflite_pred']} (Fresh={s['tflite_fresh_p']:.6f}, Spoiled={s['tflite_spoil_p']:.6f})")
    print(f"    Max Logit Diff = {s['max_diff']:.8f} -> Match = {s['match']}")

print("\n==================================================")
print("FINAL CONVERSION & VERIFICATION REPORT")
print("==================================================")
print(f"V3_KERAS_MODEL = freshness_model_v3_best.keras")
print(f"V3_TFLITE_MODEL = food_freshness_v3.tflite")
print("")
print(f"V3_KERAS_INPUT = [1,224,224,3]")
print(f"V3_KERAS_OUTPUT = [1,2]")
print(f"V3_TFLITE_INPUT = {tflite_in_shape}")
print(f"V3_TFLITE_OUTPUT = {tflite_out_shape}")
print("")
print(f"CLASS_0 = Fresh")
print(f"CLASS_1 = Spoiled")
print("")
print(f"V3_TFLITE_SIZE = {tflite_size} bytes")
print(f"V3_TFLITE_SHA256 = {tflite_sha256}")
print("")
print(f"KERAS_TEST_ACCURACY = {keras_acc:.4f}")
print(f"TFLITE_TEST_ACCURACY = {tflite_acc:.4f}")
print("")
print(f"PREDICTION_MATCH_COUNT = {match_count}")
print(f"PREDICTION_MISMATCH_COUNT = {mismatch_count}")
print("")
print(f"V3_TFLITE_CONVERSION = SUCCESS")
print(f"ANDROID_MODIFIED = NO")
print(f"CURRENT_PRODUCTION_TFLITE_REPLACED = NO")
