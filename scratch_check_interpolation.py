import tensorflow as tf
import numpy as np

model = tf.keras.models.load_model("freshness_model_best.keras")

def get_pixels_and_pred(image_path, interpolation):
    image = tf.keras.utils.load_img(
        image_path,
        target_size=(224, 224),
        color_mode="rgb",
        interpolation=interpolation
    )
    image_array = tf.keras.utils.img_to_array(image).astype(np.float32)
    flat_pixels = image_array.reshape(-1, 3)
    first_10 = flat_pixels[:10].tolist()
    
    batch = np.expand_dims(image_array, axis=0)
    probs = model.predict(batch, verbose=0)[0].tolist()
    return probs, first_10, np.min(image_array), np.max(image_array), np.mean(image_array)

for interp in ["nearest", "bilinear"]:
    print(f"\n=== INTERPOLATION: {interp} ===")
    f_prob, f_10, f_min, f_max, f_mean = get_pixels_and_pred("test_freshness/fresh.jpg", interp)
    s_prob, s_10, s_min, s_max, s_mean = get_pixels_and_pred("test_freshness/spoiled.jpg", interp)
    print(f"fresh.jpg - Probs: {f_prob}, Min: {f_min}, Max: {f_max}, Mean: {f_mean}")
    print(f"fresh.jpg - First 10 pixels: {f_10}")
    print(f"spoiled.jpg - Probs: {s_prob}, Min: {s_min}, Max: {s_max}, Mean: {s_mean}")
    print(f"spoiled.jpg - First 10 pixels: {s_10}")
