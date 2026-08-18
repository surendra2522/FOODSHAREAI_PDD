#!/usr/bin/env python3
"""
FoodShareAI - Dataset V2 Build Pipeline

Steps:
  1. Analyze current dataset quality (duplicates, near-dupes, category breakdown)
  2. Download Food-101 via TFDS and extract prepared-food Fresh examples
  3. Build freshness_dataset_v2/ (Fresh + Spoiled, no leakage)
  4. Train new MobileNetV2 2-class model
  5. Evaluate vs current model and report
"""
import os
import sys
import shutil
import hashlib
import csv
import json
import numpy as np
from pathlib import Path
from PIL import Image

# ── Paths ────────────────────────────────────────────────────────────────────
ROOT = Path(__file__).resolve().parent.parent
V1_DIR  = ROOT / "freshness_dataset"
V2_DIR  = ROOT / "freshness_dataset_v2"
TEST_DIR = ROOT / "freshness_dataset_v2_test"   # held-out test set
REPORT  = ROOT / "freshness_v2_report.txt"

# Food-101 categories we treat as FRESH prepared food
FRESH_FOOD101_CATEGORIES = [
    "biryani", "fried_rice", "pad_thai", "pizza", "pasta",
    "chicken_curry", "spring_rolls", "samosa", "naan", "pork_chop",
    "fried_egg", "hamburger", "grilled_salmon", "spaghetti_bolognese",
    "risotto", "beef_carpaccio", "bruschetta", "caesar_salad",
    "chicken_wings", "chocolate_cake", "crab_cakes", "donuts",
    "dumplings", "edamame", "eggs_benedict", "fish_and_chips",
    "french_fries", "french_onion_soup", "french_toast", "garlic_bread",
    "hot_and_sour_soup", "huevos_rancheros", "hummus", "ice_cream",
    "lasagna", "macaroni_and_cheese", "miso_soup", "moules_mariniere",
    "nachos", "omelette", "onion_rings", "oysters", "pancakes",
    "peking_duck", "pho", "pulled_pork_sandwich", "ramen", "ravioli",
    "red_velvet_cake", "shrimp_and_grits", "spaghetti_carbonara",
    "steak", "sushi", "tacos", "tiramisu", "waffles",
]

IMAGES_PER_FRESH_CATEGORY = 30  # max images to take per Food-101 category
FRESH_TOTAL_TARGET = 1200       # target Fresh total (v1 + Food-101)
SPOILED_TOTAL_TARGET = 800      # target Spoiled total (v1 only, may be less)

VAL_SPLIT   = 0.15
TEST_SPLIT  = 0.15
TRAIN_SPLIT = 1.0 - VAL_SPLIT - TEST_SPLIT
SEED = 42

# ────────────────────────────────────────────────────────────────────────────
def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

def perceptual_hash(path, size=8):
    """Simple average-hash for near-duplicate detection."""
    try:
        img = Image.open(path).convert("L").resize((size, size), Image.NEAREST)
        arr = np.array(img, dtype=np.float32)
        mean = arr.mean()
        return "".join("1" if p >= mean else "0" for p in arr.flatten())
    except Exception:
        return None

def hamming(h1, h2):
    return sum(c1 != c2 for c1, c2 in zip(h1, h2))

def is_valid(path, min_px=64):
    try:
        with Image.open(path) as img:
            w, h = img.size
            return w >= min_px and h >= min_px
    except Exception:
        return False

# ── STEP 1 — Analyse current dataset ─────────────────────────────────────────
def analyse_v1():
    print("\n" + "="*60)
    print("STEP 1 — Analysing freshness_dataset (v1)")
    print("="*60)

    report = {}
    for cls in ("Fresh", "Spoiled"):
        cls_dir = V1_DIR / cls
        files = sorted([f for f in cls_dir.iterdir()
                        if f.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp")])

        exact_hashes = {}
        phashes      = {}
        exact_dups   = 0
        near_dups    = 0
        invalid      = 0

        for f in files:
            if not is_valid(f):
                invalid += 1
                continue

            h = sha256(f)
            if h in exact_hashes:
                exact_dups += 1
                continue
            exact_hashes[h] = f

            ph = perceptual_hash(f)
            if ph:
                near_match = any(hamming(ph, existing) <= 4
                                 for existing in phashes)
                if near_match:
                    near_dups += 1
                phashes[ph] = f

        report[cls] = {
            "total": len(files),
            "exact_duplicates": exact_dups,
            "near_duplicates": near_dups,
            "invalid": invalid,
            "unique": len(exact_hashes)
        }

        print(f"\n  Class: {cls}")
        print(f"    Total files    : {len(files)}")
        print(f"    Exact dups     : {exact_dups}")
        print(f"    Near-dups (<=4) : {near_dups}")
        print(f"    Invalid images : {invalid}")
        print(f"    Unique valid   : {len(exact_hashes)}")

    # Source category breakdown
    manifest_path = V1_DIR / "manifest.csv"
    categories = {}
    if manifest_path.exists():
        with open(manifest_path, encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                src = row.get("source_file", "")
                # Extract category from filename prefix
                cat = "_".join(src.split("_")[:2]).lower()
                categories[cat] = categories.get(cat, 0) + 1

    print(f"\n  SOURCE CATEGORIES (top 20):")
    for cat, cnt in sorted(categories.items(), key=lambda x: -x[1])[:20]:
        print(f"    {cat}: {cnt}")

    return report

# ── STEP 2 — Download Food-101 and build Fresh pool ─────────────────────────
def build_fresh_pool_from_food101():
    print("\n" + "="*60)
    print("STEP 2 — Downloading Food-101 (direct tar.gz) for Fresh pool")
    print("="*60)

    pool_dir = ROOT / "freshness_downloads_temp" / "food101_pool"
    pool_dir.mkdir(parents=True, exist_ok=True)

    existing = sum(1 for _ in pool_dir.rglob("*.jpg"))
    if existing >= 500:
        print(f"  Pool already exists with {existing} images. Skipping download.")
        return pool_dir

    import urllib.request
    import tarfile

    # Food-101 public dataset (101 classes, 750 train + 250 test per class)
    FOOD101_URL  = "http://data.vision.ee.ethz.ch/cvl/food-101.tar.gz"
    tar_path     = ROOT / "freshness_downloads_temp" / "food-101.tar.gz"
    extract_root = ROOT / "freshness_downloads_temp" / "food-101"

    if not extract_root.exists():
        if not tar_path.exists():
            print(f"  Downloading Food-101 tar.gz (~5 GB) from ETH Zurich ...")
            print(f"  URL: {FOOD101_URL}")

            def progress_hook(count, block_size, total_size):
                downloaded = count * block_size
                if total_size > 0:
                    pct = downloaded * 100 / total_size
                    mb  = downloaded / (1024 * 1024)
                    if count % 2000 == 0:
                        print(f"    {pct:.1f}%  ({mb:.0f} MB)", flush=True)
                else:
                    if count % 2000 == 0:
                        print(f"    {downloaded/(1024*1024):.0f} MB downloaded", flush=True)

            urllib.request.urlretrieve(FOOD101_URL, tar_path, reporthook=progress_hook)
            print("  Download complete.")
        else:
            print(f"  Tar already present at {tar_path.name}, skipping download.")

        print("  Extracting only target categories from tar.gz ...")
        target_set = set(FRESH_FOOD101_CATEGORIES)
        with tarfile.open(tar_path, "r:gz") as tf_obj:
            members = [m for m in tf_obj.getmembers()
                       if m.name.endswith(".jpg") and
                       any(f"/images/{cat}/" in m.name for cat in target_set)]
            print(f"  Found {len(members)} target images in archive.")
            tf_obj.extractall(path=ROOT / "freshness_downloads_temp",
                              members=members)
        print("  Extraction complete.")
    else:
        print(f"  food-101/ extract already exists, skipping.")

    # Copy into pool_dir (capped per category)
    target_set = set(FRESH_FOOD101_CATEGORIES)
    counts = {cat: 0 for cat in target_set}
    saved = 0
    images_src = extract_root / "images"

    for cat in target_set:
        cat_src = images_src / cat
        if not cat_src.exists():
            continue
        cat_dst = pool_dir / cat
        cat_dst.mkdir(exist_ok=True)
        files = sorted(cat_src.glob("*.jpg"))
        for f in files[:IMAGES_PER_FRESH_CATEGORY]:
            dst = cat_dst / f.name
            if not dst.exists():
                shutil.copy2(f, dst)
            counts[cat] += 1
            saved += 1

    total = sum(counts.values())
    categories_found = [c for c in target_set if counts.get(c, 0) > 0]
    print(f"  Done. Total Food-101 fresh images in pool: {total}")
    print(f"  Categories with images ({len(categories_found)}): {sorted(categories_found)}")
    return pool_dir

# ── STEP 3 — Build freshness_dataset_v2 ──────────────────────────────────────
def build_v2(pool_dir):
    print("\n" + "="*60)
    print("STEP 3 — Building freshness_dataset_v2")
    print("="*60)

    # Clean previous v2
    if V2_DIR.exists():
        shutil.rmtree(V2_DIR)
    if TEST_DIR.exists():
        shutil.rmtree(TEST_DIR)

    for split in ("train", "val"):
        for cls in ("Fresh", "Spoiled"):
            (V2_DIR / split / cls).mkdir(parents=True, exist_ok=True)
    for cls in ("Fresh", "Spoiled"):
        (TEST_DIR / cls).mkdir(parents=True, exist_ok=True)

    seen_hashes = set()

    def copy_image(src, dest_dir, cls, split, tag):
        h = sha256(src)
        if h in seen_hashes:
            return False
        seen_hashes.add(h)
        dest = dest_dir / cls / f"{tag}_{h[:12]}{src.suffix.lower()}"
        shutil.copy2(src, dest)
        return True

    rng = np.random.default_rng(SEED)

    # ─── Fresh: existing v1 Fresh images ───
    v1_fresh = sorted([f for f in (V1_DIR / "Fresh").iterdir()
                       if f.suffix.lower() in (".jpg",".jpeg",".png",".webp")])
    rng.shuffle(v1_fresh)  # in-place

    n = len(v1_fresh)
    n_test  = max(1, int(n * TEST_SPLIT))
    n_val   = max(1, int(n * VAL_SPLIT))
    n_train = n - n_test - n_val

    for i, f in enumerate(v1_fresh):
        if i < n_train:
            copy_image(f, V2_DIR / "train", "Fresh", "train", "fresh_v1")
        elif i < n_train + n_val:
            copy_image(f, V2_DIR / "val", "Fresh", "val", "fresh_v1")
        else:
            copy_image(f, TEST_DIR, "Fresh", "test", "fresh_v1")

    # ─── Fresh: Food-101 pool ───
    food101_images = sorted(pool_dir.rglob("*.jpg"))
    rng.shuffle(food101_images)

    n = len(food101_images)
    n_test  = max(1, int(n * TEST_SPLIT))
    n_val   = max(1, int(n * VAL_SPLIT))
    n_train = n - n_test - n_val

    for i, f in enumerate(food101_images):
        if i < n_train:
            copy_image(f, V2_DIR / "train", "Fresh", "train", "fresh_f101")
        elif i < n_train + n_val:
            copy_image(f, V2_DIR / "val", "Fresh", "val", "fresh_f101")
        else:
            copy_image(f, TEST_DIR, "Fresh", "test", "fresh_f101")

    # ─── Spoiled: existing v1 Spoiled images ───
    v1_spoiled = sorted([f for f in (V1_DIR / "Spoiled").iterdir()
                         if f.suffix.lower() in (".jpg",".jpeg",".png",".webp")])
    rng.shuffle(v1_spoiled)

    n = len(v1_spoiled)
    n_test  = max(1, int(n * TEST_SPLIT))
    n_val   = max(1, int(n * VAL_SPLIT))
    n_train = n - n_test - n_val

    for i, f in enumerate(v1_spoiled):
        if i < n_train:
            copy_image(f, V2_DIR / "train", "Spoiled", "train", "spoiled_v1")
        elif i < n_train + n_val:
            copy_image(f, V2_DIR / "val", "Spoiled", "val", "spoiled_v1")
        else:
            copy_image(f, TEST_DIR, "Spoiled", "test", "spoiled_v1")

    # Count
    counts = {}
    for split in ("train", "val"):
        for cls in ("Fresh", "Spoiled"):
            k = f"{split}/{cls}"
            counts[k] = sum(1 for _ in (V2_DIR / split / cls).iterdir())
    for cls in ("Fresh", "Spoiled"):
        k = f"test/{cls}"
        counts[k] = sum(1 for _ in (TEST_DIR / cls).iterdir())

    print(f"\n  freshness_dataset_v2 counts:")
    for k, v in sorted(counts.items()):
        print(f"    {k}: {v}")
    return counts

# ── STEP 4 — Train new model ──────────────────────────────────────────────────
def train_v2_model():
    print("\n" + "="*60)
    print("STEP 4 — Training freshness_model_v2_best.keras")
    print("="*60)

    import tensorflow as tf

    IMG_SIZE   = (224, 224)
    BATCH_SIZE = 32
    AUTOTUNE   = tf.data.AUTOTUNE

    train_ds = tf.keras.utils.image_dataset_from_directory(
        str(V2_DIR / "train"),
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        label_mode="int",
        seed=SEED
    )
    val_ds = tf.keras.utils.image_dataset_from_directory(
        str(V2_DIR / "val"),
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        label_mode="int",
        seed=SEED
    )

    CLASS_NAMES = train_ds.class_names
    print(f"  CLASS_NAMES = {CLASS_NAMES}")

    # Class weights
    n_fresh   = sum(1 for _ in (V2_DIR / "train" / "Fresh").iterdir())
    n_spoiled = sum(1 for _ in (V2_DIR / "train" / "Spoiled").iterdir())
    total = n_fresh + n_spoiled
    cw = {
        CLASS_NAMES.index("Fresh"):   total / (2.0 * n_fresh),
        CLASS_NAMES.index("Spoiled"): total / (2.0 * n_spoiled)
    }
    print(f"  Class weights: {cw}")

    # Augmentation
    aug = tf.keras.Sequential([
        tf.keras.layers.RandomFlip("horizontal"),
        tf.keras.layers.RandomRotation(0.12),
        tf.keras.layers.RandomZoom(0.12),
        tf.keras.layers.RandomContrast(0.15),
        tf.keras.layers.RandomBrightness(0.10),
    ], name="augmentation")

    train_ds = (train_ds
                .map(lambda x, y: (aug(x, training=True), y),
                     num_parallel_calls=AUTOTUNE)
                .prefetch(AUTOTUNE))
    val_ds = val_ds.prefetch(AUTOTUNE)

    # Build model
    base = tf.keras.applications.MobileNetV2(
        input_shape=(224, 224, 3),
        include_top=False,
        weights="imagenet"
    )
    base.trainable = False

    inputs  = tf.keras.Input(shape=(224, 224, 3))
    x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)
    x = base(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Dropout(0.35)(x)
    x = tf.keras.layers.Dense(256, activation="relu")(x)
    x = tf.keras.layers.Dropout(0.25)(x)
    outputs = tf.keras.layers.Dense(2, activation="softmax",
                                    name="freshness_output")(x)

    model = tf.keras.Model(inputs, outputs, name="FoodShareAI_Freshness_V2")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(),
        metrics=["accuracy"]
    )

    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=5,
                                         restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.2,
                                              patience=2, min_lr=1e-7),
        tf.keras.callbacks.ModelCheckpoint(
            str(ROOT / "freshness_model_v2_best.keras"),
            monitor="val_accuracy", save_best_only=True, verbose=1
        )
    ]

    print("  Phase 1: Frozen base (15 epochs max)…")
    history = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=15,
        class_weight=cw,
        callbacks=callbacks,
        verbose=2
    )

    # Phase 2: Unfreeze top 30 layers
    print("\n  Phase 2: Fine-tuning top 30 MobileNetV2 layers (10 epochs)…")
    for layer in base.layers[-30:]:
        layer.trainable = True

    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-5),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(),
        metrics=["accuracy"]
    )
    model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=10,
        class_weight=cw,
        callbacks=callbacks,
        verbose=2
    )

    # Load best checkpoint
    model = tf.keras.models.load_model(
        str(ROOT / "freshness_model_v2_best.keras"))
    return model, CLASS_NAMES, val_ds

# ── STEP 5 — Evaluate & compare ──────────────────────────────────────────────
def evaluate(model, class_names, val_ds):
    from sklearn.metrics import classification_report, confusion_matrix
    import tensorflow as tf

    print("\n" + "="*60)
    print("STEP 5 — Evaluation")
    print("="*60)

    y_true, y_pred = [], []
    for images, labels in val_ds:
        preds = model.predict(images, verbose=0)
        y_true.extend(labels.numpy().tolist())
        y_pred.extend(np.argmax(preds, axis=1).tolist())

    y_true = np.array(y_true)
    y_pred = np.array(y_pred)

    report = classification_report(y_true, y_pred,
                                   target_names=class_names, digits=4,
                                   output_dict=True)
    report_str = classification_report(y_true, y_pred,
                                       target_names=class_names, digits=4)
    cm = confusion_matrix(y_true, y_pred)

    val_loss, val_acc = model.evaluate(val_ds, verbose=0)

    print(f"\n  VAL_ACCURACY = {val_acc:.4f}")
    print(f"  VAL_LOSS     = {val_loss:.4f}")
    print(f"\n{report_str}")
    print(f"  Confusion Matrix:\n{cm}")

    # Test set evaluation
    print("\n  --- Independent Test Set ---")
    test_ds = tf.keras.utils.image_dataset_from_directory(
        str(TEST_DIR),
        image_size=(224, 224),
        batch_size=32,
        label_mode="int",
        seed=SEED
    )
    test_class_names = test_ds.class_names
    test_ds = test_ds.prefetch(tf.data.AUTOTUNE)

    yt, yp = [], []
    for images, labels in test_ds:
        preds = model.predict(images, verbose=0)
        yt.extend(labels.numpy().tolist())
        yp.extend(np.argmax(preds, axis=1).tolist())

    yt = np.array(yt)
    yp = np.array(yp)
    test_report_str = classification_report(yt, yp,
                                            target_names=test_class_names,
                                            digits=4)
    test_acc = np.mean(yt == yp)
    print(f"  TEST_ACCURACY = {test_acc:.4f}")
    print(f"\n{test_report_str}")

    return {
        "val_accuracy":  val_acc,
        "val_loss":      val_loss,
        "report":        report,
        "report_str":    report_str,
        "test_accuracy": test_acc,
        "test_report":   test_report_str,
        "cm":            cm.tolist()
    }

# ── STEP 6 — Compare with current model ──────────────────────────────────────
def compare_current_model(new_metrics):
    import tensorflow as tf

    print("\n" + "="*60)
    print("STEP 6 — Comparing with freshness_model_best.keras (v1)")
    print("="*60)

    v1_model_path = ROOT / "freshness_model_best.keras"
    if not v1_model_path.exists():
        print("  freshness_model_best.keras not found — skipping comparison.")
        return {}

    v1_model = tf.keras.models.load_model(str(v1_model_path))

    # Re-use v1 val set for fair comparison
    v1_val_ds = tf.keras.utils.image_dataset_from_directory(
        str(V1_DIR),
        validation_split=0.20,
        subset="validation",
        seed=SEED,
        image_size=(224, 224),
        batch_size=32,
        label_mode="int"
    )
    v1_val_ds = v1_val_ds.prefetch(tf.data.AUTOTUNE)
    v1_class_names = v1_val_ds.class_names

    v1_loss, v1_acc = v1_model.evaluate(v1_val_ds, verbose=0)

    from sklearn.metrics import classification_report as cr
    yt, yp = [], []
    for images, labels in v1_val_ds:
        preds = v1_model.predict(images, verbose=0)
        yt.extend(labels.numpy().tolist())
        yp.extend(np.argmax(preds, axis=1).tolist())
    yt = np.array(yt); yp = np.array(yp)
    v1_report = cr(yt, yp, target_names=v1_class_names,
                   digits=4, output_dict=True)

    print(f"  V1 MODEL (freshness_model_best.keras):")
    print(f"    VAL_ACCURACY = {v1_acc:.4f}")
    print(f"    FRESH  F1    = {v1_report.get('Fresh',{}).get('f1-score',0):.4f}")
    print(f"    SPOILED F1   = {v1_report.get('Spoiled',{}).get('f1-score',0):.4f}")

    # Test v1 on v2 test set (independent test)
    test_ds = tf.keras.utils.image_dataset_from_directory(
        str(TEST_DIR),
        image_size=(224, 224),
        batch_size=32,
        label_mode="int",
        seed=SEED
    )
    test_ds = test_ds.prefetch(tf.data.AUTOTUNE)
    yt2, yp2 = [], []
    for images, labels in test_ds:
        preds = v1_model.predict(images, verbose=0)
        yt2.extend(labels.numpy().tolist())
        yp2.extend(np.argmax(preds, axis=1).tolist())
    yt2 = np.array(yt2); yp2 = np.array(yp2)
    v1_test_acc = np.mean(yt2 == yp2)
    print(f"    INDEPENDENT TEST ACCURACY = {v1_test_acc:.4f}")

    return {
        "v1_val_accuracy":  float(v1_acc),
        "v1_fresh_f1":   v1_report.get("Fresh",{}).get("f1-score", 0),
        "v1_spoiled_f1": v1_report.get("Spoiled",{}).get("f1-score", 0),
        "v1_test_acc":   float(v1_test_acc),
        "v1_report":     v1_report
    }

# ── STEP 7 — Write final report ───────────────────────────────────────────────
def write_report(v1_analysis, v2_counts, new_metrics, v1_comparison):
    new_rep = new_metrics.get("report", {})
    lines = [
        "FOODSHAREAI FRESHNESS — DATASET V2 & MODEL V2 REPORT",
        "="*60,
        "",
        "── CURRENT DATASET (v1) QUALITY ──",
        f"  Fresh total        : {v1_analysis.get('Fresh',{}).get('total',0)}",
        f"  Fresh exact dups   : {v1_analysis.get('Fresh',{}).get('exact_duplicates',0)}",
        f"  Fresh near-dups    : {v1_analysis.get('Fresh',{}).get('near_duplicates',0)}",
        f"  Spoiled total      : {v1_analysis.get('Spoiled',{}).get('total',0)}",
        f"  Spoiled exact dups : {v1_analysis.get('Spoiled',{}).get('exact_duplicates',0)}",
        f"  Spoiled near-dups  : {v1_analysis.get('Spoiled',{}).get('near_duplicates',0)}",
        f"  ROOT CAUSE         : Dataset contains only western grocery items",
        f"                       (bread/dairy/fruits/veg). No prepared Indian",
        f"                       meals. Domain mismatch with FoodShareAI use case.",
        "",
        "── DATASET V2 COUNTS ──",
    ]
    for k, v in sorted(v2_counts.items()):
        lines.append(f"  {k}: {v}")

    r = new_metrics.get("report", {})
    lines += [
        "",
        "── NEW MODEL (V2) PERFORMANCE ──",
        f"  VALIDATION_ACCURACY  = {new_metrics.get('val_accuracy', 0):.4f}",
        f"  VALIDATION_LOSS      = {new_metrics.get('val_loss', 0):.4f}",
        f"  FRESH  Precision     = {r.get('Fresh',{}).get('precision',0):.4f}",
        f"  FRESH  Recall        = {r.get('Fresh',{}).get('recall',0):.4f}",
        f"  FRESH  F1            = {r.get('Fresh',{}).get('f1-score',0):.4f}",
        f"  SPOILED Precision    = {r.get('Spoiled',{}).get('precision',0):.4f}",
        f"  SPOILED Recall       = {r.get('Spoiled',{}).get('recall',0):.4f}",
        f"  SPOILED F1           = {r.get('Spoiled',{}).get('f1-score',0):.4f}",
        f"  INDEPENDENT_TEST_ACC = {new_metrics.get('test_accuracy', 0):.4f}",
        "",
        "── CURRENT MODEL (V1) PERFORMANCE ──",
        f"  VALIDATION_ACCURACY  = {v1_comparison.get('v1_val_accuracy',0):.4f}",
        f"  FRESH  F1            = {v1_comparison.get('v1_fresh_f1',0):.4f}",
        f"  SPOILED F1           = {v1_comparison.get('v1_spoiled_f1',0):.4f}",
        f"  INDEPENDENT_TEST_ACC = {v1_comparison.get('v1_test_acc',0):.4f}",
        "",
        "── RECOMMENDATION ──",
    ]

    new_test = new_metrics.get("test_accuracy", 0)
    old_test = v1_comparison.get("v1_test_acc", 0)
    if new_test > old_test + 0.02:
        lines.append(f"  USE NEW MODEL V2 (test acc +{new_test-old_test:.2%})")
    else:
        lines.append(f"  V2 improvement marginal. Keep v1 unless manual review shows better generalisation.")

    lines += [
        "",
        "── SAFETY ──",
        "  MODEL_RETRAINED           = YES (freshness_model_v2_best.keras)",
        "  CURRENT_MODEL_PRESERVED   = YES (freshness_model_best.keras unchanged)",
        "  ANDROID_CHANGED           = NO",
        "  TFLITE_REPLACED           = NO",
        "  DATASET_V2_CREATED        = YES",
    ]

    content = "\n".join(lines) + "\n"
    with open(REPORT, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"\n  Report written to {REPORT.name}")
    print(content)

# ── Main ──────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    os.chdir(ROOT)

    v1_analysis = analyse_v1()

    pool_dir = build_fresh_pool_from_food101()

    v2_counts = build_v2(pool_dir)

    model, class_names, val_ds = train_v2_model()

    new_metrics = evaluate(model, class_names, val_ds)

    v1_comparison = compare_current_model(new_metrics)

    write_report(v1_analysis, v2_counts, new_metrics, v1_comparison)

    print("\n" + "="*60)
    print("PIPELINE COMPLETE")
    print("  freshness_model_v2_best.keras : saved")
    print("  freshness_model_best.keras    : unchanged")
    print("  food_freshness.tflite         : unchanged")
    print("  Android code                  : unchanged")
    print("="*60)
