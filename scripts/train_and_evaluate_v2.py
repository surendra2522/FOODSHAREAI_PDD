#!/usr/bin/env python3
"""
FoodShareAI — Freshness Model V2 Trainer & Evaluator

Dataset: freshness_dataset_v2 (Fresh: Class 0, Spoiled: Class 1)
Architecture: MobileNetV2 Transfer Learning
Outputs: freshness_model_v2_best.keras
Evaluation & Comparison with old freshness_model_best.keras
"""

import os
import sys
import shutil
from pathlib import Path
import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models
from sklearn.metrics import classification_report, confusion_matrix, precision_recall_fscore_support
from sklearn.utils.class_weight import compute_class_weight
from PIL import Image

# Workspace Configuration
WORKSPACE_ROOT = Path(__file__).resolve().parent.parent
V2_DATASET_DIR = WORKSPACE_ROOT / "freshness_dataset_v2"
V2_MODEL_SAVE_PATH = WORKSPACE_ROOT / "freshness_model_v2_best.keras"
OLD_MODEL_PATH = WORKSPACE_ROOT / "freshness_model_best.keras"
TEST_V2_DIR = WORKSPACE_ROOT / "real_food_test_v2"

BATCH_SIZE = 32
IMG_SIZE = (224, 224)
EPOCHS = 20
CLASS_NAMES = ["Fresh", "Spoiled"]

def verify_v2_dataset():
    if not V2_DATASET_DIR.exists():
        print(f"ERROR: Dataset directory '{V2_DATASET_DIR}' does not exist.")
        sys.exit(1)
    fresh_dir = V2_DATASET_DIR / "Fresh"
    spoiled_dir = V2_DATASET_DIR / "Spoiled"
    if not fresh_dir.exists() or not spoiled_dir.exists():
        print("ERROR: Class directories 'Fresh' and 'Spoiled' must exist in V2 dataset.")
        sys.exit(1)
    
    fresh_count = len([f for f in fresh_dir.glob('*') if f.is_file()])
    spoiled_count = len([f for f in spoiled_dir.glob('*') if f.is_file()])
    print(f"Verified V2 Dataset:")
    print(f"  Fresh images:   {fresh_count}")
    print(f"  Spoiled images: {spoiled_count}")
    print(f"  Total images:   {fresh_count + spoiled_count}")
    return fresh_count, spoiled_count

def build_v2_model():
    base_model = tf.keras.applications.MobileNetV2(
        input_shape=(224, 224, 3),
        include_top=False,
        weights="imagenet"
    )
    base_model.trainable = False

    inputs = tf.keras.Input(shape=(224, 224, 3), name="input_image")
    # Data Augmentation (applied during training)
    data_augmentation = tf.keras.Sequential([
        layers.RandomFlip("horizontal_and_vertical"),
        layers.RandomRotation(0.15),
        layers.RandomZoom(0.15),
        layers.RandomContrast(0.1),
    ], name="data_augmentation")

    x = data_augmentation(inputs)
    x = tf.keras.applications.mobilenet_v2.preprocess_input(x)
    x = base_model(x, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.BatchNormalization()(x)
    x = layers.Dropout(0.3)(x)
    x = layers.Dense(128, activation='relu')(x)
    x = layers.Dropout(0.2)(x)
    outputs = layers.Dense(2, activation='softmax', name='freshness_output')(x)

    model = tf.keras.Model(inputs, outputs, name="FoodShareAI_Freshness_V2")
    return model, base_model

def main():
    print("=" * 80)
    print("FoodShareAI — 2-Class Freshness V2 Training & Evaluation Pipeline")
    print("=" * 80)

    fresh_count, spoiled_count = verify_v2_dataset()

    print("\n--- 1. Loading & Splitting Dataset (80/20 Deterministic Split) ---")
    raw_train_ds = tf.keras.utils.image_dataset_from_directory(
        V2_DATASET_DIR,
        validation_split=0.2,
        subset="training",
        seed=123,
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        label_mode='int'
    )

    raw_val_ds = tf.keras.utils.image_dataset_from_directory(
        V2_DATASET_DIR,
        validation_split=0.2,
        subset="validation",
        seed=123,
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        label_mode='int'
    )

    class_names = raw_train_ds.class_names
    print(f"Class Names & Index Order: {class_names}")
    assert class_names == ["Fresh", "Spoiled"], f"Classes must be ['Fresh', 'Spoiled'], got {class_names}"

    # Calculate class weights from training dataset
    train_labels = []
    for _, labels in raw_train_ds:
        train_labels.extend(labels.numpy())
    train_labels = np.array(train_labels)

    class_weights_arr = compute_class_weight(
        class_weight="balanced",
        classes=np.unique(train_labels),
        y=train_labels
    )
    class_weight_dict = {i: float(class_weights_arr[i]) for i in range(len(class_weights_arr))}
    print(f"Calculated Class Weights (Training counts: Fresh={np.sum(train_labels==0)}, Spoiled={np.sum(train_labels==1)}):")
    print(f"  Class 0 (Fresh):   {class_weight_dict[0]:.4f}")
    print(f"  Class 1 (Spoiled): {class_weight_dict[1]:.4f}")

    AUTOTUNE = tf.data.AUTOTUNE
    train_ds = raw_train_ds.shuffle(1000).prefetch(buffer_size=AUTOTUNE)
    val_ds = raw_val_ds.prefetch(buffer_size=AUTOTUNE)

    print("\n--- 2. Constructing MobileNetV2 Architecture ---")
    model, base_model = build_v2_model()
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(),
        metrics=['accuracy']
    )
    model.summary()

    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor='val_loss', patience=5, restore_best_weights=True, verbose=1),
        tf.keras.callbacks.ReduceLROnPlateau(monitor='val_loss', patience=3, factor=0.5, verbose=1),
        tf.keras.callbacks.ModelCheckpoint(str(V2_MODEL_SAVE_PATH), monitor='val_loss', save_best_only=True, verbose=1)
    ]

    print("\n--- 3. Training V2 Model (Up to 20 Epochs) ---")
    history = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=EPOCHS,
        class_weight=class_weight_dict,
        callbacks=callbacks
    )

    print(f"\nSaved Best V2 Model to: {V2_MODEL_SAVE_PATH}")

    # Load best saved V2 model for evaluation
    best_v2_model = tf.keras.models.load_model(str(V2_MODEL_SAVE_PATH))

    print("\n==================================================")
    print("V2 MODEL EVALUATION")
    print("==================================================")

    # Calculate metrics on training set
    train_loss, train_acc = best_v2_model.evaluate(train_ds, verbose=0)
    val_loss, val_acc = best_v2_model.evaluate(val_ds, verbose=0)

    print(f"Training Accuracy:   {train_acc:.4f}")
    print(f"Validation Accuracy: {val_acc:.4f}")
    print(f"Validation Loss:     {val_loss:.4f}")

    y_val_true = []
    y_val_pred = []
    y_val_probs = []

    for images, labels in raw_val_ds:
        preds = best_v2_model.predict(images, verbose=0)
        y_val_true.extend(labels.numpy())
        y_val_pred.extend(np.argmax(preds, axis=1))
        y_val_probs.extend(preds)

    y_val_true = np.array(y_val_true)
    y_val_pred = np.array(y_val_pred)
    y_val_probs = np.array(y_val_probs)

    precision, recall, f1, _ = precision_recall_fscore_support(y_val_true, y_val_pred, average='macro')
    per_class_p, per_class_r, per_class_f1, _ = precision_recall_fscore_support(y_val_true, y_val_pred, average=None)

    cm = confusion_matrix(y_val_true, y_val_pred)

    fresh_pred_count = np.sum(y_val_pred == 0)
    spoiled_pred_count = np.sum(y_val_pred == 1)

    print(f"\nMacro Precision: {precision:.4f}")
    print(f"Macro Recall:    {recall:.4f}")
    print(f"Macro F1-Score:  {f1:.4f}")

    print("\nConfusion Matrix:")
    print(cm)

    print("\nPer-Class Results:")
    print(f"  Fresh   (Class 0): Precision={per_class_p[0]:.4f}, Recall={per_class_r[0]:.4f}, F1={per_class_f1[0]:.4f}")
    print(f"  Spoiled (Class 1): Precision={per_class_p[1]:.4f}, Recall={per_class_r[1]:.4f}, F1={per_class_f1[1]:.4f}")

    print(f"\nFresh Predictions Count:   {fresh_pred_count}")
    print(f"Spoiled Predictions Count: {spoiled_pred_count}")

    # Check model collapse
    unique_preds = np.unique(y_val_pred)
    if len(unique_preds) < 2:
        print("\nWARNING: Model Collapse Detected! Model predicted only one class.")
    else:
        print("\nModel Collapse Status: NO COLLAPSE DETECTED (Model predicts both Fresh and Spoiled classes).")

    print("\n==================================================")
    print("INDEPENDENT TEST ON real_food_test_v2/")
    print("==================================================")

    v2_test_results = []
    if TEST_V2_DIR.exists():
        test_files = sorted(list(TEST_V2_DIR.glob('*')))
        for img_path in test_files:
            if img_path.suffix.lower() not in ['.jpg', '.jpeg', '.png', '.webp']:
                continue
            
            filename = img_path.name
            actual_label = "Fresh" if "fresh" in filename.lower() else ("Spoiled" if "spoiled" in filename.lower() else "Unknown")
            
            img = Image.open(img_path).convert('RGB').resize(IMG_SIZE)
            img_arr = np.array(img, dtype=np.float32)
            img_input = np.expand_dims(img_arr, axis=0) # [1, 224, 224, 3]

            preds = best_v2_model.predict(img_input, verbose=0)[0]
            pred_class = np.argmax(preds)
            pred_label = CLASS_NAMES[pred_class]
            fresh_prob = preds[0]
            spoiled_prob = preds[1]
            confidence = preds[pred_class]

            v2_test_results.append({
                "filename": filename,
                "actual_label": actual_label,
                "predicted_label": pred_label,
                "fresh_probability": fresh_prob,
                "spoiled_probability": spoiled_prob,
                "confidence": confidence,
                "correct": (pred_label == actual_label) if actual_label != "Unknown" else None
            })

            print(f"Image: {filename}")
            print(f"  Actual Label:        {actual_label}")
            print(f"  Predicted Label:     {pred_label}")
            print(f"  Fresh Probability:   {fresh_prob:.4f}")
            print(f"  Spoiled Probability: {spoiled_prob:.4f}")
            print(f"  Confidence:          {confidence:.4f}")
            print("-" * 50)
    else:
        print(f"WARNING: Test directory '{TEST_V2_DIR}' not found.")

    v2_correct_test = sum(1 for r in v2_test_results if r["correct"] is True)
    v2_total_test = sum(1 for r in v2_test_results if r["correct"] is not None)
    v2_test_acc_str = f"{v2_correct_test}/{v2_total_test} ({v2_correct_test/v2_total_test:.2%})" if v2_total_test > 0 else "N/A"

    print("\n==================================================")
    print("COMPARISON WITH OLD MODEL (freshness_model_best.keras)")
    print("==================================================")

    old_val_acc_str = "N/A"
    old_fresh_f1_str = "N/A"
    old_spoiled_f1_str = "N/A"
    old_test_acc_str = "N/A"

    if OLD_MODEL_PATH.exists():
        try:
            print(f"Loading existing old model from: {OLD_MODEL_PATH}")
            old_model = tf.keras.models.load_model(str(OLD_MODEL_PATH))

            y_old_pred = []
            for images, labels in raw_val_ds:
                preds = old_model.predict(images, verbose=0)
                y_old_pred.extend(np.argmax(preds, axis=1))

            y_old_pred = np.array(y_old_pred)
            old_val_acc = np.mean(y_old_pred == y_val_true)
            _, _, old_per_class_f1, _ = precision_recall_fscore_support(y_val_true, y_old_pred, average=None)

            old_val_acc_str = f"{old_val_acc:.4f}"
            old_fresh_f1_str = f"{old_per_class_f1[0]:.4f}"
            old_spoiled_f1_str = f"{old_per_class_f1[1]:.4f}"

            # Evaluate old model on test_v2
            old_correct_test = 0
            old_total_test = 0
            if TEST_V2_DIR.exists():
                for img_path in sorted(list(TEST_V2_DIR.glob('*'))):
                    if img_path.suffix.lower() not in ['.jpg', '.jpeg', '.png', '.webp']:
                        continue
                    filename = img_path.name
                    actual_label = "Fresh" if "fresh" in filename.lower() else ("Spoiled" if "spoiled" in filename.lower() else "Unknown")
                    if actual_label == "Unknown":
                        continue
                    img = Image.open(img_path).convert('RGB').resize(IMG_SIZE)
                    img_arr = np.array(img, dtype=np.float32)
                    img_input = np.expand_dims(img_arr, axis=0)
                    preds = old_model.predict(img_input, verbose=0)[0]
                    pred_label = CLASS_NAMES[np.argmax(preds)]
                    old_total_test += 1
                    if pred_label == actual_label:
                        old_correct_test += 1

            old_test_acc_str = f"{old_correct_test}/{old_total_test} ({old_correct_test/old_total_test:.2%})" if old_total_test > 0 else "N/A"

        except Exception as e:
            print(f"Error evaluating old model: {e}")

    print("\n--- COMPARISON REPORT ---")
    print(f"OLD_VALIDATION_ACCURACY = {old_val_acc_str}")
    print(f"V2_VALIDATION_ACCURACY  = {val_acc:.4f}")
    print()
    print(f"OLD_FRESH_F1            = {old_fresh_f1_str}")
    print(f"V2_FRESH_F1             = {per_class_f1[0]:.4f}")
    print()
    print(f"OLD_SPOILED_F1          = {old_spoiled_f1_str}")
    print(f"V2_SPOILED_F1           = {per_class_f1[1]:.4f}")
    print()
    print(f"OLD_INDEPENDENT_TEST    = {old_test_acc_str}")
    print(f"V2_INDEPENDENT_TEST     = {v2_test_acc_str}")
    print("==================================================")

if __name__ == "__main__":
    main()
