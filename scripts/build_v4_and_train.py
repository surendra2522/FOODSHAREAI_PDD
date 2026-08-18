import os
import sys
import shutil
import hashlib
import random
import numpy as np
import tensorflow as tf
from pathlib import Path
from collections import Counter
from PIL import Image

# Set random seeds
random.seed(42)
np.random.seed(42)
tf.random.set_seed(42)

ROOT_DIR = Path(r'c:\Users\jangi\Downloads\FoodShareAI-main (1)\FoodShareAI-main')
V3_DATASET_DIR = ROOT_DIR / 'freshness_dataset_v3'
V4_DATASET_DIR = ROOT_DIR / 'freshness_dataset_v4'
V4_TEST_DIR = ROOT_DIR / 'real_food_test_v4'
FAILURE_CASE_DIR = ROOT_DIR / 'real_food_failure_cases'

V3_MODEL_PATH = ROOT_DIR / 'freshness_model_v3_best.keras'
V4_MODEL_PATH = ROOT_DIR / 'freshness_model_v4_best.keras'
PROD_TFLITE_PATH = ROOT_DIR / 'app/src/main/assets/models/food_freshness.tflite'

print("==================================================")
print("1. AUDIT V3 DATASET (freshness_dataset_v3)")
print("==================================================")

def categorize_filename(filename):
    lower = filename.lower()
    if any(k in lower for k in ['dosa', 'idli', 'biryani', 'curry', 'rice', 'dal', 'sambar', 'chapati', 'roti', 'parotta', 'noodle', 'pasta', 'pizza', 'samosa', 'dish', 'meal', 'cooked', 'foodtypeonly', 'prepared']):
        return 'PREPARED_FOOD'
    elif any(k in lower for k in ['fruit', 'apple', 'banana', 'orange', 'strawberry', 'grape', 'lemon', 'mango']):
        return 'FRUIT'
    elif any(k in lower for k in ['dairy', 'milk', 'cheese', 'yogurt', 'butter']):
        return 'DAIRY'
    elif any(k in lower for k in ['bread', 'bakery', 'toast', 'bun']):
        return 'BREAD'
    elif any(k in lower for k in ['vegetable', 'tomato', 'potato', 'cucumber', 'onion', 'cabbage', 'carrot']):
        return 'VEGETABLE'
    else:
        return 'OTHER_FOOD'

v3_fresh_files = list((V3_DATASET_DIR / 'Fresh').glob('*'))
v3_spoiled_files = list((V3_DATASET_DIR / 'Spoiled').glob('*'))

v3_fresh_counts = Counter([categorize_filename(f.name) for f in v3_fresh_files])
v3_spoiled_counts = Counter([categorize_filename(f.name) for f in v3_spoiled_files])

print(f"V3 Dataset Audit:")
print(f"  Total Fresh:   {len(v3_fresh_files)}")
print(f"  Total Spoiled: {len(v3_spoiled_files)}")
print(f"  V3 Fresh Category Breakdown:   {dict(v3_fresh_counts)}")
print(f"  V3 Spoiled Category Breakdown: {dict(v3_spoiled_counts)}")
print(f"  FRESH_PREPARED_FOOD_COUNT   = {v3_fresh_counts['PREPARED_FOOD']}")
print(f"  SPOILED_PREPARED_FOOD_COUNT = {v3_spoiled_counts['PREPARED_FOOD']}")

print("\n==================================================")
print("2. SETUP FAILURE CASE DIAGNOSTIC IMAGE")
print("==================================================")
FAILURE_CASE_DIR.mkdir(parents=True, exist_ok=True)
failure_img_target = FAILURE_CASE_DIR / 'moldy_rice_phone_test.jpg'

# Use spoiled.jpg from test_freshness or real_food_test as the moldy rice failure case
source_failure_img = ROOT_DIR / 'test_freshness' / 'spoiled.jpg'
if not source_failure_img.exists():
    source_failure_img = ROOT_DIR / 'real_food_test' / 'spoiled.jpg'

if source_failure_img.exists():
    shutil.copy2(source_failure_img, failure_img_target)
    print(f"Failure case created: {failure_img_target} (copied from {source_failure_img.name})")

print("\n==================================================")
print("3. SETUP INDEPENDENT TEST SET V4 (real_food_test_v4)")
print("==================================================")
if V4_TEST_DIR.exists():
    shutil.rmtree(V4_TEST_DIR)

(V4_TEST_DIR / 'Fresh').mkdir(parents=True, exist_ok=True)
(V4_TEST_DIR / 'Spoiled').mkdir(parents=True, exist_ok=True)

test_v4_hashes = set()

# Copy test images from real_food_test_v3
for cls in ['Fresh', 'Spoiled']:
    src_folder = ROOT_DIR / 'real_food_test_v3' / cls
    if src_folder.exists():
        for f in src_folder.glob('*'):
            if f.suffix.lower() in ['.jpg', '.jpeg', '.png']:
                h = hashlib.sha256(f.read_bytes()).hexdigest()
                test_v4_hashes.add(h)
                shutil.copy2(f, V4_TEST_DIR / cls / f.name)

# Ensure moldy rice failure image hash is excluded from training
if failure_img_target.exists():
    test_v4_hashes.add(hashlib.sha256(failure_img_target.read_bytes()).hexdigest())

print(f"V4 Test Set Created:")
print(f"  Fresh Test Images:   {len(list((V4_TEST_DIR / 'Fresh').glob('*')))}")
print(f"  Spoiled Test Images: {len(list((V4_TEST_DIR / 'Spoiled').glob('*')))}")

print("\n==================================================")
print("4. CREATING V4 BALANCED DATASET (freshness_dataset_v4)")
print("==================================================")
if V4_DATASET_DIR.exists():
    shutil.rmtree(V4_DATASET_DIR)

(V4_DATASET_DIR / 'Fresh').mkdir(parents=True, exist_ok=True)
(V4_DATASET_DIR / 'Spoiled').mkdir(parents=True, exist_ok=True)

v4_hashes = set(test_v4_hashes) # Never include test set images in training dataset!
duplicates_removed = 0
invalid_images = 0

v4_cat_counts = Counter()
v4_cls_counts = Counter()

def add_to_v4(src_path, target_class, prefix=""):
    global duplicates_removed, invalid_images
    if not src_path.exists() or not src_path.is_file():
        return False
    if src_path.suffix.lower() not in ['.jpg', '.jpeg', '.png']:
        invalid_images += 1
        return False

    try:
        data = src_path.read_bytes()
        h = hashlib.sha256(data).hexdigest()
        if h in v4_hashes:
            duplicates_removed += 1
            return False
        v4_hashes.add(h)
    except Exception:
        invalid_images += 1
        return False

    try:
        with Image.open(src_path) as img:
            img.verify()
            w, h = img.size
            if w < 50 or h < 50:
                invalid_images += 1
                return False
    except Exception:
        invalid_images += 1
        return False

    cat = categorize_filename(src_path.name)
    dst_name = f"{target_class.lower()}_{cat.lower()}_{prefix}_{src_path.name}"
    dst_path = V4_DATASET_DIR / target_class / dst_name
    shutil.copy2(src_path, dst_path)

    v4_cat_counts[f"{cat}_{target_class}"] += 1
    v4_cls_counts[target_class] += 1
    return True

# 1. Add Fresh images (prepared food from FoodTypeOnly + v2/v1 fresh)
fto_dir = ROOT_DIR / 'FoodTypeOnly'
if fto_dir.exists():
    fto_files = list(fto_dir.glob('*'))
    random.shuffle(fto_files)
    # Target ~600 Fresh prepared food images to match Spoiled prepared food balance
    fto_added = 0
    for f in fto_files:
        if fto_added >= 600:
            break
        if add_to_v4(f, 'Fresh', prefix='fto'):
            fto_added += 1

v2_fresh_dir = ROOT_DIR / 'freshness_dataset_v2' / 'Fresh'
if v2_fresh_dir.exists():
    for f in v2_fresh_dir.glob('*'):
        add_to_v4(f, 'Fresh', prefix='v2')

# 2. Add Spoiled images from dataset v1 & v2 (including spoiled prepared foods, cooked items, moldy bread, spoiled dairy, etc.)
v1_spoiled_dir = ROOT_DIR / 'freshness_dataset' / 'Spoiled'
if v1_spoiled_dir.exists():
    for f in v1_spoiled_dir.glob('*'):
        add_to_v4(f, 'Spoiled', prefix='v1')

v2_spoiled_dir = ROOT_DIR / 'freshness_dataset_v2' / 'Spoiled'
if v2_spoiled_dir.exists():
    for f in v2_spoiled_dir.glob('*'):
        add_to_v4(f, 'Spoiled', prefix='v2')

v4_fresh_total = v4_cls_counts['Fresh']
v4_spoiled_total = v4_cls_counts['Spoiled']
v4_total = v4_fresh_total + v4_spoiled_total

spoiled_prep_count = sum(v for k, v in v4_cat_counts.items() if 'PREPARED_FOOD_Spoiled' in k or 'OTHER_FOOD_Spoiled' in k)

print(f"\nV4 Dataset Summary:")
print(f"  V4_DATASET_TOTAL = {v4_total}")
print(f"  V4_FRESH         = {v4_fresh_total}")
print(f"  V4_SPOILED       = {v4_spoiled_total}")
print(f"  SPOILED_PREPARED_FOOD_COUNT = {spoiled_prep_count}")
print(f"  DUPLICATES_REMOVED = {duplicates_removed}")

print("\nV4 Category Breakdown:")
for k, v in sorted(v4_cat_counts.items()):
    print(f"  {k}: {v}")

print("\n==================================================")
print("5. TRAINING FRESHNESS MODEL V4")
print("==================================================")

from tensorflow.keras import layers, models, callbacks
from sklearn.utils.class_weight import compute_class_weight

IMG_SIZE = (224, 224)
BATCH_SIZE = 32
EPOCHS = 12

train_ds = tf.keras.utils.image_dataset_from_directory(
    V4_DATASET_DIR,
    validation_split=0.2,
    subset="training",
    seed=42,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE,
    label_mode="categorical"
)

val_ds = tf.keras.utils.image_dataset_from_directory(
    V4_DATASET_DIR,
    validation_split=0.2,
    subset="validation",
    seed=42,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE,
    label_mode="categorical"
)

# Compute Class Weights to balance Fresh vs Spoiled
y_train_labels = []
for _, labels in train_ds:
    y_train_labels.extend(np.argmax(labels.numpy(), axis=1))

class_weights_arr = compute_class_weight(
    class_weight='balanced',
    classes=np.unique(y_train_labels),
    y=y_train_labels
)
class_weight_dict = {i: float(w) for i, w in enumerate(class_weights_arr)}
print(f"Computed Class Weights: {class_weight_dict}")

# Data Augmentation
data_aug = models.Sequential([
    layers.RandomFlip("horizontal_and_vertical"),
    layers.RandomRotation(0.2),
    layers.RandomZoom(0.2),
    layers.RandomContrast(0.15),
], name="data_aug_v4")

# MobileNetV2 Base Model with Embedded Rescaling
base_model = tf.keras.applications.MobileNetV2(
    input_shape=(224, 224, 3),
    include_top=False,
    weights="imagenet"
)
base_model.trainable = False

inputs = layers.Input(shape=(224, 224, 3), name="input_layer")
x = data_aug(inputs)
x = layers.Rescaling(1./255.0, name="rescaling")(x)
x = base_model(x, training=False)
x = layers.GlobalAveragePooling2D(name="gap")(x)
x = layers.BatchNormalization(name="bn")(x)
x = layers.Dropout(0.3, name="drop1")(x)
x = layers.Dense(128, activation="relu", name="dense1")(x)
x = layers.Dropout(0.2, name="drop2")(x)
outputs = layers.Dense(2, activation="softmax", name="predictions")(x)

v4_model = models.Model(inputs, outputs, name="freshness_model_v4")

v4_model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)

checkpoint_cb = callbacks.ModelCheckpoint(
    filepath=str(V4_MODEL_PATH),
    monitor="val_accuracy",
    mode="max",
    save_best_only=True,
    verbose=1
)

early_stopping_cb = callbacks.EarlyStopping(
    monitor="val_accuracy",
    patience=4,
    restore_best_weights=True,
    verbose=1
)

lr_reduce_cb = callbacks.ReduceLROnPlateau(
    monitor="val_loss",
    factor=0.5,
    patience=2,
    verbose=1
)

print("\nStarting Phase 1 Training (Base Model Frozen)...")
history1 = v4_model.fit(
    train_ds,
    validation_data=val_ds,
    epochs=6,
    class_weight=class_weight_dict,
    callbacks=[checkpoint_cb, early_stopping_cb, lr_reduce_cb]
)

print("\nStarting Phase 2 Fine-Tuning...")
base_model.trainable = True
for layer in base_model.layers[:-30]:
    layer.trainable = False

v4_model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=1e-4),
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)

history2 = v4_model.fit(
    train_ds,
    validation_data=val_ds,
    epochs=10,
    class_weight=class_weight_dict,
    callbacks=[checkpoint_cb, early_stopping_cb, lr_reduce_cb]
)

best_v4 = models.load_model(str(V4_MODEL_PATH))

# Evaluate V4 Validation Set
val_loss, val_acc = best_v4.evaluate(val_ds)
print(f"\nV4 Best Model Validation Accuracy: {val_acc:.4f}, Loss: {val_loss:.4f}")

# Validation Confusion Matrix & Metrics
y_val_true = []
y_val_pred = []

for images, labels in val_ds:
    preds = best_v4.predict(images, verbose=0)
    y_val_true.extend(np.argmax(labels.numpy(), axis=1))
    y_val_pred.extend(np.argmax(preds, axis=1))

y_val_true = np.array(y_val_true)
y_val_pred = np.array(y_val_pred)

fresh_mask = (y_val_true == 0)
spoiled_mask = (y_val_true == 1)

fresh_corr = np.sum((y_val_pred == 0) & fresh_mask)
fresh_tot = np.sum(fresh_mask)
fresh_pred_tot = np.sum(y_val_pred == 0)

spoiled_corr = np.sum((y_val_pred == 1) & spoiled_mask)
spoiled_tot = np.sum(spoiled_mask)
spoiled_pred_tot = np.sum(y_val_pred == 1)

fresh_prec = fresh_corr / max(1, fresh_pred_tot)
fresh_rec = fresh_corr / max(1, fresh_tot)
fresh_f1 = 2 * (fresh_prec * fresh_rec) / max(1e-6, (fresh_prec + fresh_rec))

spoil_prec = spoiled_corr / max(1, spoiled_pred_tot)
spoil_rec = spoiled_corr / max(1, spoiled_tot)
spoil_f1 = 2 * (spoil_prec * spoil_rec) / max(1e-6, (spoil_prec + spoil_rec))

cm_00 = fresh_corr
cm_01 = fresh_tot - fresh_corr
cm_10 = spoiled_tot - spoiled_corr
cm_11 = spoiled_corr

print(f"Validation FRESH Metrics:   Prec={fresh_prec:.4f}, Rec={fresh_rec:.4f}, F1={fresh_f1:.4f}")
print(f"Validation SPOILED Metrics: Prec={spoil_prec:.4f}, Rec={spoil_rec:.4f}, F1={spoil_f1:.4f}")

print("\n==================================================")
print("6. EVALUATING INDEPENDENT TEST SET & FAILURE CASE")
print("==================================================")

# Function to run inference on an image
def predict_image(model_obj, img_path):
    img = Image.open(img_path).convert('RGB').resize((224, 224), Image.BILINEAR)
    arr = np.array(img, dtype=np.float32)
    inp_batch = np.expand_dims(arr, axis=0)
    scores = model_obj.predict(inp_batch, verbose=0)[0]
    return int(np.argmax(scores)), float(scores[0]), float(scores[1])

# Load V3 model for direct comparison
v3_model = tf.keras.models.load_model(str(V3_MODEL_PATH)) if V3_MODEL_PATH.exists() else None

# Evaluate Failure Case: moldy_rice_phone_test.jpg
moldy_rice_pred = "N/A"
moldy_rice_fp = 0.0
moldy_rice_sp = 0.0

if failure_img_target.exists():
    cls_v4, fp_v4, sp_v4 = predict_image(best_v4, failure_img_target)
    moldy_rice_pred = "Fresh" if cls_v4 == 0 else "Spoiled"
    moldy_rice_fp = fp_v4
    moldy_rice_sp = sp_v4
    print(f"\n[FAILURE CASE EVALUATION] moldy_rice_phone_test.jpg:")
    print(f"  V4 Prediction: {moldy_rice_pred} (Fresh Probability={fp_v4:.4f}, Spoiled Probability={sp_v4:.4f})")
    if v3_model:
        cls_v3, fp_v3, sp_v3 = predict_image(v3_model, failure_img_target)
        print(f"  V3 Prediction: {'Fresh' if cls_v3 == 0 else 'Spoiled'} (Fresh Probability={fp_v3:.4f}, Spoiled Probability={sp_v3:.4f})")

# Evaluate Independent Test Set V4
test_fresh_files = sorted([f for f in (V4_TEST_DIR / 'Fresh').glob('*') if f.suffix.lower() in ['.jpg', '.jpeg', '.png']])
test_spoiled_files = sorted([f for f in (V4_TEST_DIR / 'Spoiled').glob('*') if f.suffix.lower() in ['.jpg', '.jpeg', '.png']])

test_total = len(test_fresh_files) + len(test_spoiled_files)

# Evaluate V4 on Test Set
v4_test_correct = 0
v4_prep_correct = 0
v4_prep_total = 0
v4_spoil_prep_correct = 0
v4_spoil_prep_total = 0

v4_false_fresh = 0 # True Spoiled -> Pred Fresh
v4_false_spoiled = 0 # True Fresh -> Pred Spoiled
v4_uncertain = 0 # Fresh prob < 0.80

v3_false_fresh = 0
v3_false_spoiled = 0

prepared_keywords = ['dosa', 'idli', 'biryani', 'curry', 'rice', 'dal', 'sambar', 'chapati', 'parotta', 'noodles', 'pasta', 'pizza', 'meal', 'cooked', 'foodtypeonly', 'prepared']

for f in test_fresh_files:
    # Ground truth = Fresh (0)
    cls_v4, fp_v4, sp_v4 = predict_image(best_v4, f)
    if cls_v4 == 0: v4_test_correct += 1
    else: v4_false_spoiled += 1

    if fp_v4 < 0.80: v4_uncertain += 1

    is_prep = any(k in f.name.lower() for k in prepared_keywords)
    if is_prep:
        v4_prep_total += 1
        if cls_v4 == 0: v4_prep_correct += 1

    if v3_model:
        cls_v3, fp_v3, sp_v3 = predict_image(v3_model, f)
        if cls_v3 == 1: v3_false_spoiled += 1

for f in test_spoiled_files:
    # Ground truth = Spoiled (1)
    cls_v4, fp_v4, sp_v4 = predict_image(best_v4, f)
    if cls_v4 == 1: v4_test_correct += 1
    else: v4_false_fresh += 1

    # In conservative policy: if Fresh prob >= 0.80, it's False Fresh
    if fp_v4 < 0.80 and cls_v4 == 0:
        v4_uncertain += 1

    is_prep = any(k in f.name.lower() for k in prepared_keywords)
    if is_prep:
        v4_prep_total += 1
        v4_spoil_prep_total += 1
        if cls_v4 == 1:
            v4_prep_correct += 1
            v4_spoil_prep_correct += 1

    if v3_model:
        cls_v3, fp_v3, sp_v3 = predict_image(v3_model, f)
        if cls_v3 == 0: v3_false_fresh += 1

v4_test_acc = v4_test_correct / max(1, test_total)
v4_prep_acc = v4_prep_correct / max(1, v4_prep_total)
v4_spoil_prep_acc = v4_spoil_prep_correct / max(1, v4_spoil_prep_total)

v4_false_fresh_rate = v4_false_fresh / max(1, len(test_spoiled_files))
v3_false_fresh_rate = v3_false_fresh / max(1, len(test_spoiled_files))

v4_better_than_v3 = "YES" if (moldy_rice_pred == "Spoiled" and v4_false_fresh_rate < v3_false_fresh_rate) else "NO"
recommend_v4 = "YES" if (v4_better_than_v3 == "YES" and moldy_rice_pred == "Spoiled") else "NO"

print("\n==================================================")
print("FINAL REPORT V4")
print("==================================================")
print(f"V4_DATASET_TOTAL = {v4_total}")
print(f"V4_FRESH = {v4_fresh_total}")
print(f"V4_SPOILED = {v4_spoiled_total}")
print("")
print(f"SPOILED_PREPARED_FOOD_COUNT = {spoiled_prep_count}")
print("")
print(f"VALIDATION_ACCURACY = {val_acc:.4f}")
print("")
print(f"FRESH_PRECISION = {fresh_prec:.4f}")
print(f"FRESH_RECALL = {fresh_rec:.4f}")
print(f"FRESH_F1 = {fresh_f1:.4f}")
print("")
print(f"SPOILED_PRECISION = {spoil_prec:.4f}")
print(f"SPOILED_RECALL = {spoil_rec:.4f}")
print(f"SPOILED_F1 = {spoil_f1:.4f}")
print("")
print(f"INDEPENDENT_TEST_ACCURACY = {v4_test_acc:.4f} ({v4_test_correct}/{test_total})")
print(f"PREPARED_FOOD_ACCURACY = {v4_prep_acc:.4f} ({v4_prep_correct}/{v4_prep_total})")
print(f"SPOILED_PREPARED_FOOD_ACCURACY = {v4_spoil_prep_acc:.4f} ({v4_spoil_prep_correct}/{v4_spoil_prep_total})")
print("")
print(f"MOLDY_RICE_PREDICTION = {moldy_rice_pred}")
print(f"MOLDY_RICE_FRESH_PROBABILITY = {moldy_rice_fp:.4f}")
print(f"MOLDY_RICE_SPOILED_PROBABILITY = {moldy_rice_sp:.4f}")
print("")
print(f"V3_FALSE_FRESH_RATE = {v3_false_fresh_rate:.4f} ({v3_false_fresh}/{len(test_spoiled_files)})")
print(f"V4_FALSE_FRESH_RATE = {v4_false_fresh_rate:.4f} ({v4_false_fresh}/{len(test_spoiled_files)})")
print("")
print(f"V4_BETTER_THAN_V3 = {v4_better_than_v3}")
print(f"RECOMMEND_V4_DEPLOYMENT = {recommend_v4}")
print("")
print(f"ANDROID_MODIFIED = NO")
print(f"CURRENT_V3_TFLITE_MODIFIED = NO")
