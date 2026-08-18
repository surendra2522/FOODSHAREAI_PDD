import tensorflow as tf
import numpy as np
from PIL import Image

interpreter = tf.lite.Interpreter(model_path="app/src/main/assets/models/food_freshness.tflite")
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

def predict_pil(image_path, resample):
    img = Image.open(image_path).convert('RGB').resize((224, 224), resample=resample)
    input_data = np.array(img, dtype=np.float32)[None, ...]
    interpreter.set_tensor(input_details[0]['index'], input_data)
    interpreter.invoke()
    probs = interpreter.get_tensor(output_details[0]['index'])[0].tolist()
    return probs, np.min(input_data), np.max(input_data), np.mean(input_data)

resamplers = {
    "NEAREST": Image.NEAREST,
    "BILINEAR": Image.BILINEAR,
    "BICUBIC": Image.BICUBIC,
    "LANCZOS": Image.LANCZOS
}

for name, res in resamplers.items():
    print(f"\n--- Resampling: {name} ---")
    f_prob, f_min, f_max, f_mean = predict_pil("test_freshness/fresh.jpg", res)
    s_prob, s_min, s_max, s_mean = predict_pil("test_freshness/spoiled.jpg", res)
    print(f"fresh.jpg: {f_prob}, mean={f_mean}")
    print(f"spoiled.jpg: {s_prob}, mean={s_mean}")
