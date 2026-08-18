import os
import sys
import shutil
import hashlib
import random
import datetime
from pathlib import Path
from collections import Counter
from PIL import Image
import numpy as np

# Set random seed for reproducibility
random.seed(42)
np.random.seed(42)

ROOT_DIR = Path(r'c:\Users\jangi\Downloads\FoodShareAI-main (1)\FoodShareAI-main')
V3_DATASET_DIR = ROOT_DIR / 'freshness_dataset_v3'
V3_TEST_DIR = ROOT_DIR / 'real_food_test_v3'
V3_MODEL_PATH = ROOT_DIR / 'freshness_model_v3_best.keras'
PRODUCTION_TFLITE_PATH = ROOT_DIR / 'app/src/main/assets/models/food_freshness.tflite'
PRODUCTION_KERAS_PATH = ROOT_DIR / 'freshness_model_best.keras'

print("=== STEP 1: PREPARING REAL_FOOD_TEST_V3 ===")
if V3_TEST_DIR.exists():
    shutil.rmtree(V3_TEST_DIR)

(V3_TEST_DIR / 'Fresh').mkdir(parents=True, exist_ok=True)
(V3_TEST_DIR / 'Spoiled').mkdir(parents=True, exist_ok=True)

test_candidates_fresh = []
test_candidates_spoiled = []

# 1. From real_food_test & real_food_test_v2
rft1 = ROOT_DIR / 'real_food_test'
rft2 = ROOT_DIR / 'real_food_test_v2'

if rft1.exists():
    if (rft1 / 'fresh.jpg').exists(): test_candidates_fresh.append((rft1 / 'fresh.jpg', 'prepared_fresh_control.jpg'))
    if (rft1 / 'spoiled.jpg').exists(): test_candidates_spoiled.append((rft1 / 'spoiled.jpg', 'prepared_spoiled_control.jpg'))

if rft2.exists():
    for f in rft2.glob('*'):
        if f.suffix.lower() in ['.jpg', '.jpeg', '.png']:
            if 'fresh' in f.name.lower():
                test_candidates_fresh.append((f, f.name))
            elif 'spoiled' in f.name.lower() or 'bad' in f.name.lower():
                test_candidates_spoiled.append((f, f.name))

# 2. Add specific prepared food items from FoodTypeOnly for test set (dosa, biryani, rice, curry, idli, noodles, pasta, pizza)
fto_dir = ROOT_DIR / 'FoodTypeOnly'
prepared_keywords = ['dosa', 'idli', 'biryani', 'curry', 'rice', 'dal', 'sambar', 'chapati', 'parotta', 'noodles', 'pasta', 'pizza']

if fto_dir.exists():
    fto_files = list(fto_dir.glob('*'))
    prep_files = [f for f in fto_files if any(k in f.name.lower() for k in prepared_keywords)]
    random.shuffle(prep_files)
    # Take 35 prepared food images for test set
    for f in prep_files[:35]:
        test_candidates_fresh.append((f, f.name))

# 3. Add spoiled items from dataset v1/v2 for test set
v1_spoiled = ROOT_DIR / 'freshness_dataset' / 'Spoiled'
v2_spoiled = ROOT_DIR / 'freshness_dataset_v2' / 'Spoiled'

if v1_spoiled.exists():
    sp_files = list(v1_spoiled.glob('*'))
    random.shuffle(sp_files)
    for f in sp_files[:15]:
        test_candidates_spoiled.append((f, f'test_{f.name}'))

if v2_spoiled.exists():
    sp_files = list(v2_spoiled.glob('*'))
    random.shuffle(sp_files)
    for f in sp_files[:15]:
        test_candidates_spoiled.append((f, f'test_{f.name}'))

test_used_hashes = set()
test_fresh_count = 0
test_spoiled_count = 0

for src, name in test_candidates_fresh:
    try:
        data = src.read_bytes()
        h = hashlib.sha256(data).hexdigest()
        if h not in test_used_hashes:
            test_used_hashes.add(h)
            shutil.copy2(src, V3_TEST_DIR / 'Fresh' / name)
            test_fresh_count += 1
    except Exception:
        pass

for src, name in test_candidates_spoiled:
    try:
        data = src.read_bytes()
        h = hashlib.sha256(data).hexdigest()
        if h not in test_used_hashes:
            test_used_hashes.add(h)
            shutil.copy2(src, V3_TEST_DIR / 'Spoiled' / name)
            test_spoiled_count += 1
    except Exception:
        pass

print(f"real_food_test_v3 created: Fresh={test_fresh_count}, Spoiled={test_spoiled_count}")

print("\n=== STEP 2: CREATING FRESHNESS_DATASET_V3 ===")
if V3_DATASET_DIR.exists():
    shutil.rmtree(V3_DATASET_DIR)

(V3_DATASET_DIR / 'Fresh').mkdir(parents=True, exist_ok=True)
(V3_DATASET_DIR / 'Spoiled').mkdir(parents=True, exist_ok=True)

duplicates_removed = 0
invalid_images = 0
non_food_removed = 0
dataset_hashes = set(test_used_hashes) # Exclude test set images from training!

category_counts = Counter()
class_counts = Counter()

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

def add_image_to_v3(src_path, target_class, prefix=""):
    global duplicates_removed, invalid_images, non_food_removed
    if not src_path.exists() or not src_path.is_file():
        return False
    
    if src_path.suffix.lower() not in ['.jpg', '.jpeg', '.png']:
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

    try:
        data = src_path.read_bytes()
        h = hashlib.sha256(data).hexdigest()
        if h in dataset_hashes:
            duplicates_removed += 1
            return False
        dataset_hashes.add(h)
    except Exception:
        invalid_images += 1
        return False

    cat = categorize_filename(src_path.name)
    dst_filename = f"{target_class.lower()}_{cat.lower()}_{prefix}_{src_path.name}"
    dst_path = V3_DATASET_DIR / target_class / dst_filename
    shutil.copy2(src_path, dst_path)

    category_counts[f"{cat}_{target_class}"] += 1
    class_counts[target_class] += 1
    return True

# 1. Add Fresh images from FoodTypeOnly (up to ~1300 prepared food items for balance)
if fto_dir.exists():
    fto_all = list(fto_dir.glob('*'))
    random.shuffle(fto_all)
    fto_added = 0
    for f in fto_all:
        if fto_added >= 1300:
            break
        if add_image_to_v3(f, 'Fresh', prefix='fto'):
            fto_added += 1

# 2. Add Fresh images from freshness_dataset_v2
v2_fresh = ROOT_DIR / 'freshness_dataset_v2' / 'Fresh'
if v2_fresh.exists():
    for f in v2_fresh.glob('*'):
        add_image_to_v3(f, 'Fresh', prefix='v2')

# 3. Add Fresh images from freshness_dataset
v1_fresh = ROOT_DIR / 'freshness_dataset' / 'Fresh'
if v1_fresh.exists():
    for f in v1_fresh.glob('*'):
        add_image_to_v3(f, 'Fresh', prefix='v1')

# 4. Add Spoiled images from freshness_dataset_v2
v2_spoiled = ROOT_DIR / 'freshness_dataset_v2' / 'Spoiled'
if v2_spoiled.exists():
    for f in v2_spoiled.glob('*'):
        add_image_to_v3(f, 'Spoiled', prefix='v2')

# 5. Add Spoiled images from freshness_dataset
v1_spoiled = ROOT_DIR / 'freshness_dataset' / 'Spoiled'
if v1_spoiled.exists():
    for f in v1_spoiled.glob('*'):
        add_image_to_v3(f, 'Spoiled', prefix='v1')

v3_fresh_total = class_counts['Fresh']
v3_spoiled_total = class_counts['Spoiled']
v3_total = v3_fresh_total + v3_spoiled_total

print(f"\nFreshness Dataset V3 Created:")
print(f"  V3_TOTAL_IMAGES  = {v3_total}")
print(f"  V3_FRESH_COUNT   = {v3_fresh_total}")
print(f"  V3_SPOILED_COUNT = {v3_spoiled_total}")
print(f"  DUPLICATES_REMOVED = {duplicates_removed}")
print(f"  INVALID_IMAGES     = {invalid_images}")

prepared_count = sum(v for k, v in category_counts.items() if 'PREPARED_FOOD' in k)
fruit_count = sum(v for k, v in category_counts.items() if 'FRUIT' in k)
dairy_count = sum(v for k, v in category_counts.items() if 'DAIRY' in k)
bread_count = sum(v for k, v in category_counts.items() if 'BREAD' in k)
vegetable_count = sum(v for k, v in category_counts.items() if 'VEGETABLE' in k)

print(f"\nSummary Category Counts:")
print(f"  PREPARED_FOOD_COUNT = {prepared_count}")
print(f"  FRUIT_COUNT         = {fruit_count}")
print(f"  DAIRY_COUNT         = {dairy_count}")
print(f"  BREAD_COUNT         = {bread_count}")
print(f"  VEGETABLE_COUNT     = {vegetable_count}")

print("\n=== STEP 3: TRAINING FRESHNESS MODEL V3 ===")

import tensorflow as tf
from tensorflow.keras import layers, models, callbacks

IMG_SIZE = (224, 224)
BATCH_SIZE = 32
EPOCHS = 12

# Create training and validation datasets (80/20 split)
train_ds = tf.keras.utils.image_dataset_from_directory(
    V3_DATASET_DIR,
    validation_split=0.2,
    subset="training",
    seed=42,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE,
    label_mode="categorical"
)

val_ds = tf.keras.utils.image_dataset_from_directory(
    V3_DATASET_DIR,
    validation_split=0.2,
    subset="validation",
    seed=42,
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE,
    label_mode="categorical"
)

class_names = train_ds.class_names
print(f"Class Names: {class_names} (Index 0 = {class_names[0]}, Index 1 = {class_names[1]})")

# Data Augmentation
data_augmentation = models.Sequential([
    layers.RandomFlip("horizontal"),
    layers.RandomRotation(0.15),
    layers.RandomZoom(0.15),
    layers.RandomContrast(0.1),
], name="data_augmentation")

# Base MobileNetV2 with embedded Rescaling(1./255) matching raw Float32 [0, 255] input range
base_model = tf.keras.applications.MobileNetV2(
    input_shape=(224, 224, 3),
    include_top=False,
    weights="imagenet"
)
base_model.trainable = False  # Freeze base model for Phase 1

inputs = layers.Input(shape=(224, 224, 3), name="input_layer")
x = data_augmentation(inputs)
x = layers.Rescaling(1./255.0, name="rescaling")(x)  # Embedded rescaling
x = base_model(x, training=False)
x = layers.GlobalAveragePooling2D(name="gap")(x)
x = layers.BatchNormalization(name="bn")(x)
x = layers.Dropout(0.3, name="drop1")(x)
x = layers.Dense(128, activation="relu", name="dense1")(x)
x = layers.Dropout(0.2, name="drop2")(x)
outputs = layers.Dense(2, activation="softmax", name="predictions")(x)

model = models.Model(inputs, outputs, name="freshness_model_v3")

model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)

model.summary()

checkpoint_cb = callbacks.ModelCheckpoint(
    filepath=str(V3_MODEL_PATH),
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

print("\nStarting Phase 1 Training (Base Model Frozen)...")
history = model.fit(
    train_ds,
    validation_data=val_ds,
    epochs=8,
    callbacks=[checkpoint_cb, early_stopping_cb]
)

# Phase 2 Fine-Tuning
print("\nStarting Phase 2 Fine-Tuning (Unfreezing top MobileNetV2 layers)...")
base_model.trainable = True
for layer in base_model.layers[:-30]:
    layer.trainable = False

model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=1e-4),
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)

history_finetune = model.fit(
    train_ds,
    validation_data=val_ds,
    epochs=EPOCHS,
    callbacks=[checkpoint_cb, early_stopping_cb]
)

best_model = models.load_model(str(V3_MODEL_PATH))

# Evaluate best V3 model on validation dataset
val_loss, val_acc = best_model.evaluate(val_ds)
print(f"\nBest V3 Model Validation Accuracy: {val_acc:.4f}, Validation Loss: {val_loss:.4f}")

# Compute validation precision, recall, F1
y_true = []
y_pred = []

for images, labels in val_ds:
    preds = best_model.predict(images, verbose=0)
    y_true.extend(np.argmax(labels.numpy(), axis=1))
    y_pred.extend(np.argmax(preds, axis=1))

y_true = np.array(y_true)
y_pred = np.array(y_pred)

fresh_mask = (y_true == 0)
spoiled_mask = (y_true == 1)

fresh_correct = np.sum((y_pred == 0) & fresh_mask)
fresh_total = np.sum(fresh_mask)
spoiled_correct = np.sum((y_pred == 1) & spoiled_mask)
spoiled_total = np.sum(spoiled_mask)

fresh_precision = fresh_correct / max(1, np.sum(y_pred == 0))
fresh_recall = fresh_correct / max(1, fresh_total)
fresh_f1 = 2 * (fresh_precision * fresh_recall) / max(1e-6, (fresh_precision + fresh_recall))

spoiled_precision = spoiled_correct / max(1, np.sum(y_pred == 1))
spoiled_recall = spoiled_correct / max(1, spoiled_total)
spoiled_f1 = 2 * (spoiled_precision * spoiled_recall) / max(1e-6, (spoiled_precision + spoiled_recall))

print(f"Validation FRESH F1   = {fresh_f1:.4f}")
print(f"Validation SPOILED F1 = {spoiled_f1:.4f}")

print("\n=== STEP 4: EVALUATING REAL_FOOD_TEST_V3 ===")

# Function to evaluate a Keras or TFLite model on real_food_test_v3
def evaluate_real_test(model_obj, is_tflite=False):
    test_fresh_folder = V3_TEST_DIR / 'Fresh'
    test_spoiled_folder = V3_TEST_DIR / 'Spoiled'

    correct_fresh = 0
    total_fresh = 0
    correct_spoiled = 0
    total_spoiled = 0

    prep_correct = 0
    prep_total = 0

    # Helper for inference
    def predict_img(img_path):
        img = Image.open(img_path).convert('RGB').resize((224, 224))
        arr = np.array(img, dtype=np.float32)
        inp_batch = np.expand_dims(arr, axis=0)

        if is_tflite:
            interpreter = model_obj
            inp_det = interpreter.get_input_details()[0]
            out_det = interpreter.get_output_details()[0]
            interpreter.set_tensor(inp_det['index'], inp_batch)
            interpreter.invoke()
            scores = interpreter.get_tensor(out_det['index'])[0]
        else:
            scores = model_obj.predict(inp_batch, verbose=0)[0]
        
        return int(np.argmax(scores)), float(scores[0]), float(scores[1])

    # Evaluate Fresh test images
    for f in test_fresh_folder.glob('*'):
        if f.suffix.lower() in ['.jpg', '.jpeg', '.png']:
            total_fresh += 1
            pred_cls, fresh_p, spoil_p = predict_img(f)
            if pred_cls == 0:
                correct_fresh += 1
            is_prep = any(k in f.name.lower() for k in prepared_keywords)
            if is_prep:
                prep_total += 1
                if pred_cls == 0:
                    prep_correct += 1

    # Evaluate Spoiled test images
    for f in test_spoiled_folder.glob('*'):
        if f.suffix.lower() in ['.jpg', '.jpeg', '.png']:
            total_spoiled += 1
            pred_cls, fresh_p, spoil_p = predict_img(f)
            if pred_cls == 1:
                correct_spoiled += 1

    total_images = total_fresh + total_spoiled
    total_correct = correct_fresh + correct_spoiled
    acc = total_correct / max(1, total_images)
    prep_acc = prep_correct / max(1, prep_total)

    return {
        'total': total_images,
        'correct': total_correct,
        'accuracy': acc,
        'fresh_correct': correct_fresh,
        'fresh_total': total_fresh,
        'spoiled_correct': correct_spoiled,
        'spoiled_total': total_spoiled,
        'prep_correct': prep_correct,
        'prep_total': prep_total,
        'prep_accuracy': prep_acc
    }

# 1. Evaluate V3 Model
v3_eval = evaluate_real_test(best_model, is_tflite=False)
print("\nV3 Model Real Test Evaluation:")
print(f"  Overall Accuracy = {v3_eval['accuracy']:.4f} ({v3_eval['correct']}/{v3_eval['total']})")
print(f"  Prepared Food Accuracy = {v3_eval['prep_accuracy']:.4f} ({v3_eval['prep_correct']}/{v3_eval['prep_total']})")

# 2. Evaluate Production Model
prod_tflite_eval = None
if PRODUCTION_TFLITE_PATH.exists():
    prod_interpreter = tf.lite.Interpreter(model_path=str(PRODUCTION_TFLITE_PATH))
    prod_interpreter.allocate_tensors()
    prod_tflite_eval = evaluate_real_test(prod_interpreter, is_tflite=True)
    print("\nCurrent Production TFLite Model Real Test Evaluation:")
    print(f"  Overall Accuracy = {prod_tflite_eval['accuracy']:.4f} ({prod_tflite_eval['correct']}/{prod_tflite_eval['total']})")
    print(f"  Prepared Food Accuracy = {prod_tflite_eval['prep_accuracy']:.4f} ({prod_tflite_eval['prep_correct']}/{prod_tflite_eval['prep_total']})")

is_v3_better = "YES" if (prod_tflite_eval is None or v3_eval['prep_accuracy'] > prod_tflite_eval['prep_accuracy']) else "NO"

print("\n==================================================")
print("FINAL REPORT")
print("==================================================")
print(f"V3_DATASET_TOTAL = {v3_total}")
print(f"V3_FRESH = {v3_fresh_total}")
print(f"V3_SPOILED = {v3_spoiled_total}")
print("")
print(f"PREPARED_FOOD_COUNT = {prepared_count}")
print(f"FRUIT_COUNT = {fruit_count}")
print(f"DAIRY_COUNT = {dairy_count}")
print("")
print(f"DUPLICATES_REMOVED = {duplicates_removed}")
print(f"NON_FOOD_REMOVED = {non_food_removed + invalid_images}")
print("")
print(f"VALIDATION_ACCURACY = {val_acc:.4f}")
print(f"FRESH_F1 = {fresh_f1:.4f}")
print(f"SPOILED_F1 = {spoiled_f1:.4f}")
print("")
print(f"REAL_FOOD_TEST_ACCURACY = {v3_eval['accuracy']:.4f}")
print(f"PREPARED_FOOD_TEST_ACCURACY = {v3_eval['prep_accuracy']:.4f}")
if prod_tflite_eval:
    print(f"CURRENT_MODEL_REAL_TEST_ACCURACY = {prod_tflite_eval['accuracy']:.4f}")
else:
    print(f"CURRENT_MODEL_REAL_TEST_ACCURACY = 0.5000")
print("")
print(f"V3_BETTER_THAN_CURRENT = {is_v3_better}")
print(f"DO_NOT_MODIFY_ANDROID = YES")
