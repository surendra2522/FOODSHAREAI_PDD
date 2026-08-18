#!/usr/bin/env python3
"""
FoodShareAI — 2-Class Food Visual Freshness Model Trainer & TFLite Exporter

Classes:
  0: Fresh
  1: Spoiled

Architecture: MobileNetV2 Transfer Learning
Outputs: food_freshness.tflite, freshness_labels.txt
"""

import os
import sys
import shutil
import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models
import matplotlib.pyplot as plt
from sklearn.metrics import classification_report, confusion_matrix
import seaborn as sns
from PIL import Image

# Configuration
BATCH_SIZE = 32
IMG_SIZE = (224, 224)
AUTOTUNE = tf.data.AUTOTUNE
EPOCHS_HEAD = 10
EPOCHS_FINETUNE = 10

def verify_dataset(dataset_dir):
    """Verifies existence of dataset directory and class subdirectories."""
    if not os.path.exists(dataset_dir):
        print(f"ERROR: Dataset directory '{dataset_dir}' does not exist.")
        return False
    
    found_classes = sorted([d for d in os.listdir(dataset_dir) if os.path.isdir(os.path.join(dataset_dir, d))])
    print(f"Found class subdirectories in '{dataset_dir}': {found_classes}")
    
    expected = ["Fresh", "Spoiled"]
    if found_classes != expected:
        print(f"ERROR: Expected exactly classes {expected}, but found {found_classes}")
        return False
    return True

def create_model(num_classes=2):
    """Builds MobileNetV2 Transfer Learning Model (No built-in augmentation layer)."""
    base_model = tf.keras.applications.MobileNetV2(
        input_shape=(224, 224, 3),
        include_top=False,
        weights='imagenet'
    )
    base_model.trainable = False

    inputs = tf.keras.Input(shape=(224, 224, 3), name="input_image")
    # Rescale inputs to [-1, 1] for MobileNetV2
    x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)
    x = base_model(x, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.BatchNormalization()(x)
    x = layers.Dropout(0.3)(x)
    x = layers.Dense(128, activation='relu')(x)
    x = layers.Dropout(0.2)(x)
    outputs = layers.Dense(num_classes, activation='softmax', name='freshness_output')(x)

    model = tf.keras.Model(inputs, outputs, name="FoodShareAI_Freshness_Classifier")
    return model, base_model

def train_freshness_pipeline(dataset_dir, output_dir="output"):
    """Full training, evaluation, and TFLite conversion pipeline."""
    os.makedirs(output_dir, exist_ok=True)
    
    if not verify_dataset(dataset_dir):
        print("Dataset verification failed. Aborting training.")
        sys.exit(1)

    print("\n--- 1. Loading & Splitting Dataset (80/20) ---")
    raw_train_ds = tf.keras.utils.image_dataset_from_directory(
        dataset_dir,
        validation_split=0.2,
        subset="training",
        seed=123,
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE
    )

    raw_val_ds = tf.keras.utils.image_dataset_from_directory(
        dataset_dir,
        validation_split=0.2,
        subset="validation",
        seed=123,
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE
    )

    class_names = raw_train_ds.class_names
    num_classes = len(class_names)
    print(f"Derived Dataset Classes (Order-preserved): {class_names}")

    # Data Augmentation Sequentials (applied only to training dataset)
    data_augmentation = tf.keras.Sequential([
        layers.RandomFlip("horizontal_and_vertical"),
        layers.RandomRotation(0.15),
        layers.RandomZoom(0.15),
        layers.RandomContrast(0.1),
    ], name="data_augmentation")

    train_ds = raw_train_ds.map(
        lambda x, y: (data_augmentation(x, training=True), y),
        num_parallel_calls=AUTOTUNE
    )

    train_ds = train_ds.cache().shuffle(1000).prefetch(buffer_size=AUTOTUNE)
    val_ds = raw_val_ds.cache().prefetch(buffer_size=AUTOTUNE)

    print("\n--- 2. Constructing MobileNetV2 Architecture ---")
    model, base_model = create_model(num_classes=num_classes)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(),
        metrics=['accuracy']
    )
    model.summary()

    print("\n--- 3. Phase 1: Training Classification Head ---")
    callbacks = [
        tf.keras.callbacks.EarlyStopping(patience=5, restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(patience=3, factor=0.5, verbose=1)
    ]

    history_head = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=EPOCHS_HEAD,
        callbacks=callbacks
    )

    print("\n--- 4. Phase 2: Fine-Tuning Backbone Layers ---")
    base_model.trainable = True
    # Freeze bottom layers, unfreeze top 30 layers
    for layer in base_model.layers[:-30]:
        layer.trainable = False

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(),
        metrics=['accuracy']
    )

    history_finetune = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=EPOCHS_FINETUNE,
        callbacks=callbacks
    )

    print("\n--- 5. Evaluating Model Performance ---")
    # Evaluate validation loss and accuracy
    val_loss, val_acc = model.evaluate(val_ds, verbose=0)
    print(f"Validation Loss: {val_loss:.4f}")
    print(f"Validation Accuracy: {val_acc:.4f}")

    y_true = []
    y_pred = []
    for images, labels in val_ds:
        preds = model.predict(images, verbose=0)
        y_true.extend(labels.numpy())
        y_pred.extend(np.argmax(preds, axis=1))

    unique_preds = np.unique(y_pred)
    print(f"Unique predicted classes on validation set: {unique_preds}")

    # Check if the model has clearly failed to learn or if one class is completely ignored
    if len(unique_preds) < 2:
        print("ERROR: Model predicted only one class! Failed to learn class differences. Aborting TFLite conversion.")
        sys.exit(1)
    if val_acc < 0.8:
        print(f"ERROR: Model validation accuracy ({val_acc:.2%}) is too low (expected >= 80%). Aborting TFLite conversion.")
        sys.exit(1)

    print("\nClassification Report:")
    report = classification_report(y_true, y_pred, target_names=class_names)
    print(report)

    # Save Confusion Matrix plot
    cm = confusion_matrix(y_true, y_pred)
    plt.figure(figsize=(6, 5))
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', xticklabels=class_names, yticklabels=class_names)
    plt.title('Food Freshness Confusion Matrix')
    plt.xlabel('Predicted')
    plt.ylabel('Actual')
    cm_path = os.path.join(output_dir, "confusion_matrix.png")
    plt.savefig(cm_path)
    print(f"Saved confusion matrix to '{cm_path}'")

    print("\n--- 6. Testing Model on Separate Verification Images ---")
    test_folder = os.path.join(os.path.dirname(os.path.dirname(__file__)), "test_freshness")
    test_files = [
        ("fresh.jpg", "Fresh"),
        ("spoiled.jpg", "Spoiled")
    ]
    
    for filename, actual_label in test_files:
        filepath = os.path.join(test_folder, filename)
        if os.path.exists(filepath):
            img = Image.open(filepath).resize(IMG_SIZE)
            img_arr = np.array(img, dtype=np.float32)
            img_input = np.expand_dims(img_arr, axis=0)  # Input shape [1, 224, 224, 3]
            
            preds = model.predict(img_input, verbose=0)[0]
            pred_class = np.argmax(preds)
            pred_label = class_names[pred_class]
            confidence = preds[pred_class]
            
            print(f"Image: {filename}")
            print(f"  Actual Label:    {actual_label}")
            print(f"  Predicted Label: {pred_label}")
            print(f"  Confidence:      {confidence:.4f}")
        else:
            print(f"WARNING: Test image '{filepath}' not found.")

    print("\n--- 7. Converting & Exporting TensorFlow Lite Model ---")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    tflite_path = os.path.join(output_dir, "food_freshness.tflite")
    with open(tflite_path, "wb") as f:
        f.write(tflite_model)
    print(f"Exported TFLite Model to '{tflite_path}' ({len(tflite_model):,} bytes)")

    labels_path = os.path.join(output_dir, "freshness_labels.txt")
    with open(labels_path, "w", encoding="utf-8") as f:
        for label in class_names:
            f.write(f"{label}\n")
    print(f"Exported Freshness Labels to '{labels_path}'")

    print("\n--- 8. Verifying Exported TFLite Model ---")
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    input_shape = input_details[0]['shape']
    output_shape = output_details[0]['shape']

    print(f"VERIFIED Input Shape:  {input_shape}")
    print(f"VERIFIED Input Type:   {input_details[0]['dtype']}")
    print(f"VERIFIED Output Shape: {output_shape}")
    print(f"VERIFIED Output Type:  {output_details[0]['dtype']}")

    # Verification assertions
    assert list(input_shape) == [1, 224, 224, 3], f"ERROR: Input shape is {input_shape}, expected [1, 224, 224, 3]!"
    assert list(output_shape) == [1, 2], f"ERROR: Output shape is {output_shape}, expected [1, 2]!"
    print("\nSUCCESS: Exported TFLite freshness model is verified with EXACTLY [1, 224, 224, 3] input and [1, 2] output shapes!")

    # Copying to Android assets
    android_assets_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "app", "src", "main", "assets", "models")
    if os.path.exists(android_assets_dir):
        dest_tflite = os.path.join(android_assets_dir, "food_freshness.tflite")
        dest_labels = os.path.join(android_assets_dir, "freshness_labels.txt")
        shutil.copy2(tflite_path, dest_tflite)
        shutil.copy2(labels_path, dest_labels)
        print(f"SUCCESS: Copied TFLite model and label files to Android assets: {android_assets_dir}")
    else:
        print(f"WARNING: Android assets directory '{android_assets_dir}' not found. Skipping auto-copy.")

if __name__ == "__main__":
    dataset_path = sys.argv[1] if len(sys.argv) > 1 else "freshness_dataset"
    train_freshness_pipeline(dataset_path)
