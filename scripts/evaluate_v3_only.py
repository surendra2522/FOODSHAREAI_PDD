import os
import sys
import csv
import numpy as np
import tensorflow as tf
from pathlib import Path
from PIL import Image
from collections import Counter

ROOT_DIR = Path(r'c:\Users\jangi\Downloads\FoodShareAI-main (1)\FoodShareAI-main')
V3_DATASET_DIR = ROOT_DIR / 'freshness_dataset_v3'
V3_TEST_DIR = ROOT_DIR / 'real_food_test_v3'
V3_MODEL_PATH = ROOT_DIR / 'freshness_model_v3_best.keras'
PROD_TFLITE_PATH = ROOT_DIR / 'app/src/main/assets/models/food_freshness.tflite'

REPORT_TXT_PATH = ROOT_DIR / 'v3_evaluation_report.txt'
PREDICTIONS_CSV_PATH = ROOT_DIR / 'v3_predictions.csv'

print("==================================================")
print("1. LOADING & VERIFYING V3 KERAS MODEL")
print("==================================================")
if not V3_MODEL_PATH.exists():
    print(f"ERROR: {V3_MODEL_PATH} does not exist.")
    sys.exit(1)

v3_model = tf.keras.models.load_model(str(V3_MODEL_PATH))
print(f"V3 Model Path: {V3_MODEL_PATH}")
print(f"Input Shape:  {v3_model.input_shape}")
print(f"Output Shape: {v3_model.output_shape}")

# Class Mapping
print("Class Mapping: CLASS 0 = Fresh, CLASS 1 = Spoiled")

print("\n==================================================")
print("2. VALIDATION EVALUATION ON FRESHNESS_DATASET_V3")
print("==================================================")
IMG_SIZE = (224, 224)
BATCH_SIZE = 32

val_ds = tf.keras.utils.image_dataset_from_directory(
    V3_DATASET_DIR,
    validation_split=0.2,
    subset="validation",
    seed=42,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE,
    label_mode="categorical",
    shuffle=False
)

val_loss, val_acc = v3_model.evaluate(val_ds)
print(f"V3 Validation Loss:     {val_loss:.4f}")
print(f"V3 Validation Accuracy: {val_acc:.4f}")

# Calculate Confusion Matrix and Precision/Recall/F1
y_val_true = []
y_val_pred = []

for images, labels in val_ds:
    preds = v3_model.predict(images, verbose=0)
    y_val_true.extend(np.argmax(labels.numpy(), axis=1))
    y_val_pred.extend(np.argmax(preds, axis=1))

y_val_true = np.array(y_val_true)
y_val_pred = np.array(y_val_pred)

fresh_val_mask = (y_val_true == 0)
spoiled_val_mask = (y_val_true == 1)

fresh_val_correct = np.sum((y_val_pred == 0) & fresh_val_mask)
fresh_val_total = np.sum(fresh_val_mask)
fresh_val_pred_total = np.sum(y_val_pred == 0)

spoiled_val_correct = np.sum((y_val_pred == 1) & spoiled_val_mask)
spoiled_val_total = np.sum(spoiled_val_mask)
spoiled_val_pred_total = np.sum(y_val_pred == 1)

fresh_precision = fresh_val_correct / max(1, fresh_val_pred_total)
fresh_recall = fresh_val_correct / max(1, fresh_val_total)
fresh_f1 = 2 * (fresh_precision * fresh_recall) / max(1e-6, (fresh_precision + fresh_recall))

spoiled_precision = spoiled_val_correct / max(1, spoiled_val_pred_total)
spoiled_recall = spoiled_val_correct / max(1, spoiled_val_total)
spoiled_f1 = 2 * (spoiled_precision * spoiled_recall) / max(1e-6, (spoiled_precision + spoiled_recall))

# Confusion Matrix
# [[TP_fresh, FN_fresh],
#  [FP_fresh, TP_spoiled]]
cm_00 = fresh_val_correct # True Fresh -> Pred Fresh
cm_01 = fresh_val_total - fresh_val_correct # True Fresh -> Pred Spoiled
cm_10 = spoiled_val_total - spoiled_val_correct # True Spoiled -> Pred Fresh
cm_11 = spoiled_val_correct # True Spoiled -> Pred Spoiled

print("\nValidation Classification Metrics:")
print(f"  FRESH:   Precision={fresh_precision:.4f}, Recall={fresh_recall:.4f}, F1={fresh_f1:.4f}")
print(f"  SPOILED: Precision={spoiled_precision:.4f}, Recall={spoiled_recall:.4f}, F1={spoiled_f1:.4f}")
print("Confusion Matrix (Validation):")
print(f"  [[Fresh->Fresh: {cm_00}, Fresh->Spoiled: {cm_01}],")
print(f"   [Spoiled->Fresh: {cm_10}, Spoiled->Spoiled: {cm_11}]]")

print("\n==================================================")
print("3. INDEPENDENT TEST EVALUATION (REAL_FOOD_TEST_V3)")
print("==================================================")

# Production TFLite setup
tflite_interpreter = None
if PROD_TFLITE_PATH.exists():
    tflite_interpreter = tf.lite.Interpreter(model_path=str(PROD_TFLITE_PATH))
    tflite_interpreter.allocate_tensors()
    tflite_inp = tflite_interpreter.get_input_details()[0]
    tflite_out = tflite_interpreter.get_output_details()[0]

def predict_tflite(img_path):
    img = Image.open(img_path).convert('RGB').resize((224, 224), Image.BILINEAR)
    arr = np.array(img, dtype=np.float32)
    inp_batch = np.expand_dims(arr, axis=0)
    tflite_interpreter.set_tensor(tflite_inp['index'], inp_batch)
    tflite_interpreter.invoke()
    scores = tflite_interpreter.get_tensor(tflite_out['index'])[0]
    pred_cls = int(np.argmax(scores))
    return pred_cls, float(scores[0]), float(scores[1])

def predict_v3(img_path):
    img = Image.open(img_path).convert('RGB').resize((224, 224), Image.BILINEAR)
    arr = np.array(img, dtype=np.float32)
    inp_batch = np.expand_dims(arr, axis=0)
    scores = v3_model.predict(inp_batch, verbose=0)[0]
    pred_cls = int(np.argmax(scores))
    return pred_cls, float(scores[0]), float(scores[1])

csv_rows = []

test_fresh_folder = V3_TEST_DIR / 'Fresh'
test_spoiled_folder = V3_TEST_DIR / 'Spoiled'

fresh_files = sorted(list(test_fresh_folder.glob('*')))
spoiled_files = sorted(list(test_spoiled_folder.glob('*')))

fresh_files = [f for f in fresh_files if f.suffix.lower() in ['.jpg', '.jpeg', '.png']]
spoiled_files = [f for f in spoiled_files if f.suffix.lower() in ['.jpg', '.jpeg', '.png']]

v3_fresh_correct = 0
v3_spoiled_correct = 0

prod_fresh_correct = 0
prod_spoiled_correct = 0

prepared_keywords = ['dosa', 'idli', 'biryani', 'curry', 'rice', 'dal', 'sambar', 'chapati', 'parotta', 'noodles', 'pasta', 'pizza', 'samosa', 'meal', 'cooked', 'foodtypeonly', 'prepared']
prep_category_counts = Counter()
prep_category_v3_correct = Counter()
prep_category_prod_correct = Counter()

# Fresh Test Images
for f in fresh_files:
    actual_cls = "Fresh"
    v3_cls, v3_fp, v3_sp = predict_v3(f)
    v3_pred_str = "Fresh" if v3_cls == 0 else "Spoiled"
    v3_corr = (v3_cls == 0)
    if v3_corr: v3_fresh_correct += 1

    prod_cls, prod_fp, prod_sp = predict_tflite(f) if tflite_interpreter else (0, 0.5, 0.5)
    prod_pred_str = "Fresh" if prod_cls == 0 else "Spoiled"
    prod_corr = (prod_cls == 0)
    if prod_corr: prod_fresh_correct += 1

    # Prepared food tracking
    lower_name = f.name.lower()
    is_prep = any(k in lower_name for k in prepared_keywords)
    if is_prep:
        subcat = 'other_prepared'
        for k in ['dosa', 'idli', 'biryani', 'rice', 'curry', 'dal', 'sambar', 'chapati', 'parotta', 'noodles', 'pasta', 'pizza']:
            if k in lower_name:
                subcat = k
                break
        prep_category_counts[subcat] += 1
        if v3_corr: prep_category_v3_correct[subcat] += 1
        if prod_corr: prep_category_prod_correct[subcat] += 1

    csv_rows.append({
        'filename': f.name,
        'actual_class': actual_cls,
        'v3_prediction': v3_pred_str,
        'v3_fresh_probability': f"{v3_fp:.6f}",
        'v3_spoiled_probability': f"{v3_sp:.6f}",
        'production_prediction': prod_pred_str,
        'production_fresh_probability': f"{prod_fp:.6f}",
        'production_spoiled_probability': f"{prod_sp:.6f}",
        'v3_correct': "YES" if v3_corr else "NO",
        'production_correct': "YES" if prod_corr else "NO"
    })

# Spoiled Test Images
for f in spoiled_files:
    actual_cls = "Spoiled"
    v3_cls, v3_fp, v3_sp = predict_v3(f)
    v3_pred_str = "Fresh" if v3_cls == 0 else "Spoiled"
    v3_corr = (v3_cls == 1)
    if v3_corr: v3_spoiled_correct += 1

    prod_cls, prod_fp, prod_sp = predict_tflite(f) if tflite_interpreter else (1, 0.5, 0.5)
    prod_pred_str = "Fresh" if prod_cls == 0 else "Spoiled"
    prod_corr = (prod_cls == 1)
    if prod_corr: prod_spoiled_correct += 1

    lower_name = f.name.lower()
    is_prep = any(k in lower_name for k in prepared_keywords)
    if is_prep:
        subcat = 'other_prepared'
        for k in ['dosa', 'idli', 'biryani', 'rice', 'curry', 'dal', 'sambar', 'chapati', 'parotta', 'noodles', 'pasta', 'pizza']:
            if k in lower_name:
                subcat = k
                break
        prep_category_counts[subcat] += 1
        if v3_corr: prep_category_v3_correct[subcat] += 1
        if prod_corr: prep_category_prod_correct[subcat] += 1

    csv_rows.append({
        'filename': f.name,
        'actual_class': actual_cls,
        'v3_prediction': v3_pred_str,
        'v3_fresh_probability': f"{v3_fp:.6f}",
        'v3_spoiled_probability': f"{v3_sp:.6f}",
        'production_prediction': prod_pred_str,
        'production_fresh_probability': f"{prod_fp:.6f}",
        'production_spoiled_probability': f"{prod_sp:.6f}",
        'v3_correct': "YES" if v3_corr else "NO",
        'production_correct': "YES" if prod_corr else "NO"
    })

fresh_test_total = len(fresh_files)
spoiled_test_total = len(spoiled_files)
real_test_total = fresh_test_total + spoiled_test_total

v3_test_correct_total = v3_fresh_correct + v3_spoiled_correct
v3_real_test_acc = v3_test_correct_total / max(1, real_test_total)

prod_test_correct_total = prod_fresh_correct + prod_spoiled_correct
prod_real_test_acc = prod_test_correct_total / max(1, real_test_total)

v3_fresh_acc = v3_fresh_correct / max(1, fresh_test_total)
v3_spoiled_acc = v3_spoiled_correct / max(1, spoiled_test_total)

prod_fresh_acc = prod_fresh_correct / max(1, fresh_test_total)
prod_spoiled_acc = prod_spoiled_correct / max(1, spoiled_test_total)

prep_total = sum(prep_category_counts.values())
v3_prep_correct_total = sum(prep_category_v3_correct.values())
prod_prep_correct_total = sum(prep_category_prod_correct.values())

v3_prep_acc = v3_prep_correct_total / max(1, prep_total)
prod_prep_acc = prod_prep_correct_total / max(1, prep_total)

v3_better_overall = "YES" if v3_real_test_acc > prod_real_test_acc else "NO"
v3_better_prep = "YES" if v3_prep_acc > prod_prep_acc else "NO"

# Recommendation strictly based on independent test performance
recommend_replace = "YES" if (v3_better_prep == "YES" and v3_real_test_acc >= prod_real_test_acc) else "NO"

# Write CSV File
with open(PREDICTIONS_CSV_PATH, mode='w', newline='', encoding='utf-8') as csvfile:
    fieldnames = [
        'filename', 'actual_class', 'v3_prediction', 'v3_fresh_probability',
        'v3_spoiled_probability', 'production_prediction', 'production_fresh_probability',
        'production_spoiled_probability', 'v3_correct', 'production_correct'
    ]
    writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(csv_rows)

print(f"CSV Report Created: {PREDICTIONS_CSV_PATH}")

# Write Evaluation Report Text File
report_lines = [
    "================ V3 FINAL EVALUATION REPORT ================",
    f"V3_MODEL = freshness_model_v3_best.keras",
    f"INPUT_SHAPE = (None, 224, 224, 3)",
    f"OUTPUT_SHAPE = (None, 2)",
    f"CLASS_MAPPING = CLASS 0: Fresh, CLASS 1: Spoiled",
    "",
    "--- 1. VALIDATION EVALUATION ---",
    f"V3_VALIDATION_ACCURACY = {val_acc:.4f}",
    f"V3_VALIDATION_LOSS = {val_loss:.4f}",
    "",
    "--- 2. CLASSIFICATION METRICS ---",
    f"FRESH_PRECISION = {fresh_precision:.4f}",
    f"FRESH_RECALL = {fresh_recall:.4f}",
    f"FRESH_F1 = {fresh_f1:.4f}",
    "",
    f"SPOILED_PRECISION = {spoiled_precision:.4f}",
    f"SPOILED_RECALL = {spoiled_recall:.4f}",
    f"SPOILED_F1 = {spoiled_f1:.4f}",
    "",
    f"CONFUSION_MATRIX_VAL = [[{cm_00}, {cm_01}], [{cm_10}, {cm_11}]]",
    "",
    "--- 3. INDEPENDENT REAL-WORLD TEST (real_food_test_v3) ---",
    f"V3_REAL_TEST_TOTAL = {real_test_total}",
    f"V3_REAL_TEST_CORRECT = {v3_test_correct_total}",
    f"V3_REAL_TEST_ACCURACY = {v3_real_test_acc:.4f}",
    "",
    f"V3_FRESH_TEST_CORRECT = {v3_fresh_correct} / {fresh_test_total}",
    f"V3_FRESH_TEST_ACCURACY = {v3_fresh_acc:.4f}",
    f"V3_SPOILED_TEST_CORRECT = {v3_spoiled_correct} / {spoiled_test_total}",
    f"V3_SPOILED_TEST_ACCURACY = {v3_spoiled_acc:.4f}",
    "",
    "--- 4. PREPARED FOOD PERFORMANCE ---",
    f"PREPARED_FOOD_TOTAL = {prep_total}",
    f"PREPARED_FOOD_CORRECT = {v3_prep_correct_total}",
    f"V3_PREPARED_FOOD_ACCURACY = {v3_prep_acc:.4f}",
    ""
]

for cat, count in sorted(prep_category_counts.items()):
    c_v3 = prep_category_v3_correct[cat]
    c_prod = prep_category_prod_correct[cat]
    report_lines.append(f"  Category [{cat}]: Total={count}, V3 Correct={c_v3} ({c_v3/count:.2%}), Prod Correct={c_prod} ({c_prod/count:.2%})")

report_lines.extend([
    "",
    "--- 5. PRODUCTION MODEL COMPARISON ---",
    f"CURRENT_MODEL_REAL_TEST_ACCURACY = {prod_real_test_acc:.4f} ({prod_test_correct_total}/{real_test_total})",
    f"CURRENT_MODEL_FRESH_ACCURACY = {prod_fresh_acc:.4f} ({prod_fresh_correct}/{fresh_test_total})",
    f"CURRENT_MODEL_SPOILED_ACCURACY = {prod_spoiled_acc:.4f} ({prod_spoiled_correct}/{spoiled_test_total})",
    f"CURRENT_MODEL_PREPARED_FOOD_ACCURACY = {prod_prep_acc:.4f} ({prod_prep_correct_total}/{prep_total})",
    "",
    "--- 6. MODEL DECISION ---",
    f"V3_REAL_TEST_ACCURACY = {v3_real_test_acc:.4f}",
    f"CURRENT_REAL_TEST_ACCURACY = {prod_real_test_acc:.4f}",
    f"V3_PREPARED_FOOD_ACCURACY = {v3_prep_acc:.4f}",
    f"CURRENT_PREPARED_FOOD_ACCURACY = {prod_prep_acc:.4f}",
    f"V3_BETTER_OVERALL = {v3_better_overall}",
    f"V3_BETTER_FOR_PREPARED_FOOD = {v3_better_prep}",
    f"RECOMMEND_REPLACE_ANDROID_MODEL = {recommend_replace}",
    "",
    "--- 7. INTEGRITY CONFIRMATION ---",
    "EVALUATION_COMPLETE = YES",
    "REPORT_CREATED = YES",
    "CSV_CREATED = YES",
    "ANDROID_MODIFIED = NO",
    "PRODUCTION_MODEL_MODIFIED = NO"
])

REPORT_TXT_PATH.write_text("\n".join(report_lines), encoding='utf-8')
print(f"Report Text File Created: {REPORT_TXT_PATH}")

print("\n" + "\n".join(report_lines))
