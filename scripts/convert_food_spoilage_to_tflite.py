#!/usr/bin/env python3
"""
Converts food_spoilage_model (SavedModel) to TensorFlow Lite flatbuffer (food_freshness.tflite),
verifies model input/output tensor shapes and dtypes, and tests inference on fresh.jpg and spoiled.jpg.
"""

import os
import numpy as np
import tensorflow as tf
from PIL import Image

def convert_and_verify():
    saved_model_dir = "food_spoilage_model"
    asset_target_path = "app/src/main/assets/models/food_freshness.tflite"
    fresh_img_path = "test_freshness/fresh.jpg"
    spoiled_img_path = "test_freshness/spoiled.jpg"

    if not os.path.exists(saved_model_dir):
        raise FileNotFoundError(f"SavedModel directory '{saved_model_dir}' does not exist.")

    print("Step 1: Converting SavedModel to TFLite...")
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    os.makedirs(os.path.dirname(asset_target_path), exist_ok=True)
    with open(asset_target_path, "wb") as f:
        f.write(tflite_model)
    print(f"Successfully saved TFLite model ({len(tflite_model)} bytes) to '{asset_target_path}'.")

    print("\nStep 2: Inspecting and Verifying TFLite Model...")
    interpreter = tf.lite.Interpreter(model_path=asset_target_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    in_shape = input_details[0]['shape'].tolist()
    in_dtype = str(input_details[0]['dtype'])
    out_shape = output_details[0]['shape'].tolist()
    out_dtype = str(output_details[0]['dtype'])

    print(f"  Input Shape:  {in_shape}")
    print(f"  Input Dtype:  {in_dtype}")
    print(f"  Output Shape: {out_shape}")
    print(f"  Output Dtype: {out_dtype}")

    assert out_shape[1] == 2, f"Expected 2-class output shape [1, 2], got {out_shape}"

    print("\nStep 3: Running TFLite Inference Tests...")
    def run_tflite_inference(img_path):
        img = Image.open(img_path).convert('RGB').resize((224, 224))
        # Input preprocessing: float32 in range [0, 255] (matching embedded Rescaling(1/255.0) layer)
        input_data = np.array(img, dtype=np.float32)[None, ...]
        interpreter.set_tensor(input_details[0]['index'], input_data)
        interpreter.invoke()
        output_data = interpreter.get_tensor(output_details[0]['index'])[0]
        return output_data

    fresh_out = run_tflite_inference(fresh_img_path)
    spoiled_out = run_tflite_inference(spoiled_img_path)

    print(f"  FRESH ({fresh_img_path}):   Output={fresh_out}, Predicted Class={np.argmax(fresh_out)}")
    print(f"  SPOILED ({spoiled_img_path}): Output={spoiled_out}, Predicted Class={np.argmax(spoiled_out)}")
    print("\nTFLite Model Conversion and Verification Complete!")

if __name__ == "__main__":
    convert_and_verify()
