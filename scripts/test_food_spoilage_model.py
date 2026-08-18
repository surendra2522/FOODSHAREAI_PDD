#!/usr/bin/env python3
"""
Test script for validating food_spoilage_model with test_freshness images.
Applies the actual required preprocessing ([0, 255] float32 RGB input, resized to 224x224).
"""

import os
import numpy as np
import tensorflow as tf
from PIL import Image

def test_model():
    model_dir = "food_spoilage_model"
    fresh_path = "test_freshness/fresh.jpg"
    spoiled_path = "test_freshness/spoiled.jpg"

    if not os.path.exists(model_dir):
        print(f"ERROR: Model directory '{model_dir}' not found.")
        return

    if not os.path.exists(fresh_path) or not os.path.exists(spoiled_path):
        print(f"ERROR: Test images not found at '{fresh_path}' or '{spoiled_path}'.")
        return

    print("Loading food_spoilage_model SavedModel...")
    saved_model = tf.saved_model.load(model_dir)
    infer_fn = saved_model.signatures['serving_default']

    # Preprocessing: Load image, convert RGB, resize to (224, 224), float32 array in range [0, 255]
    # (Model contains embedded Rescaling(scale=1/255.0) layer as Layer 1)
    fresh_img = Image.open(fresh_path).convert('RGB').resize((224, 224))
    spoiled_img = Image.open(spoiled_path).convert('RGB').resize((224, 224))

    fresh_tensor = tf.cast(np.array(fresh_img, dtype=np.float32)[None, ...], tf.float32)
    spoiled_tensor = tf.cast(np.array(spoiled_img, dtype=np.float32)[None, ...], tf.float32)

    # Run inference
    fresh_output = infer_fn(input_1=fresh_tensor)['pred'].numpy()[0]
    spoiled_output = infer_fn(input_1=spoiled_tensor)['pred'].numpy()[0]

    fresh_class = int(np.argmax(fresh_output))
    fresh_conf = float(fresh_output[fresh_class])

    spoiled_class = int(np.argmax(spoiled_output))
    spoiled_conf = float(spoiled_output[spoiled_class])

    diff_class0 = abs(float(fresh_output[0]) - float(spoiled_output[0]))
    diff_class1 = abs(float(fresh_output[1]) - float(spoiled_output[1]))
    max_diff = max(diff_class0, diff_class1)

    print("\n" + "="*60)
    print("INFERENCE RESULTS")
    print("="*60)
    print(f"FRESH IMAGE ({fresh_path}):")
    print(f"  Raw Output:       {fresh_output}")
    print(f"  Predicted Class:  {fresh_class}")
    print(f"  Confidence:       {fresh_conf:.8f} ({fresh_conf*100:.6f}%)")

    print(f"\nSPOILED IMAGE ({spoiled_path}):")
    print(f"  Raw Output:       {spoiled_output}")
    print(f"  Predicted Class:  {spoiled_class}")
    print(f"  Confidence:       {spoiled_conf:.8f} ({spoiled_conf*100:.6f}%)")

    print("\n" + "="*60)
    print("COMPARISON SUMMARY")
    print("="*60)
    print(f"Max Absolute Output Difference: {max_diff:.8f}")

    predictions_different = "YES" if (fresh_class != spoiled_class or max_diff > 0.1) else "NO"
    print(f"Predictions Meaningfully Different: {predictions_different}")
    print("="*60 + "\n")

if __name__ == "__main__":
    test_model()
