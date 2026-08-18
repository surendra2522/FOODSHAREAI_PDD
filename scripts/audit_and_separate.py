import os
import shutil
import hashlib
import csv
import tarfile
from pathlib import Path
from PIL import Image

WORKSPACE = Path(__file__).resolve().parent.parent
RAW_DIR = WORKSPACE / "freshness_downloads_temp"
V2_DIR = WORKSPACE / "freshness_dataset_v2"
FRESH_DIR = V2_DIR / "Fresh"
SPOILED_DIR = V2_DIR / "Spoiled"
FOOD_TYPE_DIR = V2_DIR / "FoodTypeOnly"
MANIFEST_PATH = WORKSPACE / "manifest_v2.csv"
REPORT_PATH = WORKSPACE / "dataset_audit_v2.txt"

CATEGORIES_TO_TRACK = [
    "Rice", "Biryani", "Dosa", "Idli", "Sambar", "Curry", "Dal", 
    "Chapati", "Roti", "Parotta", "Fried Rice", "Noodles", "Pasta", 
    "Pizza", "Bread", "Cheese", "Other prepared foods"
]

def map_category(name):
    name = name.lower()
    if "fried rice" in name or "fried_rice" in name: return "Fried Rice"
    if "biryani" in name: return "Biryani"
    if "rice" in name: return "Rice"
    if "dosa" in name: return "Dosa"
    if "idli" in name: return "Idli"
    if "sambar" in name: return "Sambar"
    if "curry" in name: return "Curry"
    if "dal" in name or "dhal" in name: return "Dal"
    if "chapati" in name or "chappati" in name: return "Chapati"
    if "roti" in name: return "Roti"
    if "parotta" in name or "paratha" in name: return "Parotta"
    if "noodle" in name or "ramen" in name or "pho" in name or "pad_thai" in name: return "Noodles"
    if "pasta" in name or "spaghetti" in name or "macaroni" in name or "ravioli" in name: return "Pasta"
    if "pizza" in name: return "Pizza"
    if "bread" in name or "toast" in name or "sandwich" in name: return "Bread"
    if "cheese" in name: return "Cheese"
    return "Other prepared foods"

def is_valid_image(path):
    try:
        with Image.open(path) as img:
            w, h = img.size
            if w < 32 or h < 32: return False
            ratio = max(w/h, h/w)
            if ratio > 5.0: return False
            img.verify()
        return True
    except:
        return False

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

def main():
    print("Starting dataset audit and separation...")
    if V2_DIR.exists():
        shutil.rmtree(V2_DIR)
    
    FRESH_DIR.mkdir(parents=True, exist_ok=True)
    SPOILED_DIR.mkdir(parents=True, exist_ok=True)
    FOOD_TYPE_DIR.mkdir(parents=True, exist_ok=True)

    manifest_rows = []
    seen_hashes = set()
    
    stats = {
        "TOTAL_IMAGES": 0,
        "FRESH": 0,
        "SPOILED": 0,
        "FOOD_TYPE_ONLY": 0,
        "INVALID": 0,
        "DUPLICATES_REMOVED": 0
    }
    
    cat_counts = {
        "Fresh": {c: 0 for c in CATEGORIES_TO_TRACK},
        "Spoiled": {c: 0 for c in CATEGORIES_TO_TRACK}
    }

    def process_file(src_path, dest_dir, original_label, mapped_label, src_name, cat_name):
        nonlocal stats
        if not str(src_path).lower().endswith(('.jpg', '.jpeg', '.png', '.webp')):
            return

        if not is_valid_image(src_path):
            stats["INVALID"] += 1
            return
            
        h = sha256(src_path)
        if h in seen_hashes:
            stats["DUPLICATES_REMOVED"] += 1
            return
            
        seen_hashes.add(h)
        
        dest_filename = f"{mapped_label.lower()}_{cat_name}_{h[:12]}{Path(src_path).suffix.lower()}"
        dest_path = dest_dir / dest_filename
        shutil.copy2(src_path, dest_path)
        
        stats["TOTAL_IMAGES"] += 1
        if mapped_label == "Fresh":
            stats["FRESH"] += 1
            mapped_cat = map_category(cat_name)
            cat_counts["Fresh"][mapped_cat] += 1
        elif mapped_label == "Spoiled":
            stats["SPOILED"] += 1
            mapped_cat = map_category(cat_name)
            cat_counts["Spoiled"][mapped_cat] += 1
        elif mapped_label == "FoodTypeOnly":
            stats["FOOD_TYPE_ONLY"] += 1

        manifest_rows.append({
            "filename": dest_filename,
            "source_dataset": src_name,
            "original_label": original_label,
            "mapped_label": mapped_label,
            "food_category": cat_name,
            "sha256": h
        })

    # 1. Process Kaggle (Fresh/Spoiled)
    kaggle_dir = RAW_DIR / "kaggle_fresh_spoiled" / "dataset"
    if kaggle_dir.exists():
        print("Processing Kaggle dataset...")
        for root, dirs, files in os.walk(kaggle_dir):
            folder_name = Path(root).name.lower()
            mapped_label = None
            if "fresh" in folder_name: mapped_label = "Fresh"
            elif "spoiled" in folder_name or "stale" in folder_name or "rotten" in folder_name: mapped_label = "Spoiled"
            
            if mapped_label:
                cat_name = folder_name.replace("fresh_", "").replace("spoiled_", "").replace("stale_", "").replace("rotten_", "")
                dest_dir = FRESH_DIR if mapped_label == "Fresh" else SPOILED_DIR
                
                for f in files:
                    process_file(Path(root) / f, dest_dir, folder_name, mapped_label, "Kaggle", cat_name)
    else:
        print("Kaggle directory not found.")

    # 2. Process Mendeley if extracted in RAW_DIR (just in case they exist)
    for p in RAW_DIR.glob("**/*"):
        if p.is_file() and p.suffix.lower() in ['.jpg', '.jpeg', '.png']:
            path_str = str(p).lower()
            if "kaggle_fresh_spoiled" in path_str: continue # Already processed
            if "food101" in path_str or "food-101" in path_str: continue # Handled later
            
            # Heuristic for label
            mapped_label = None
            if "fresh" in path_str or "good" in p.parent.name.lower(): mapped_label = "Fresh"
            elif "spoiled" in path_str or "bad" in p.parent.name.lower(): mapped_label = "Spoiled"
            
            if mapped_label:
                cat_name = "unknown"
                if "rice" in path_str: cat_name = "rice"
                elif "parotta" in path_str: cat_name = "parotta"
                
                dest_dir = FRESH_DIR if mapped_label == "Fresh" else SPOILED_DIR
                process_file(p, dest_dir, p.parent.name, mapped_label, "Mendeley or Other", cat_name)

    # 3. Process Food-101 from food101_pool
    food101_dir = RAW_DIR / "food101_pool"
    if food101_dir.exists():
        print("Processing Food-101 extracted images...")
        for root, dirs, files in os.walk(food101_dir):
            cat_name = Path(root).name
            for f in files:
                p = Path(root) / f
                if p.suffix.lower() in ['.jpg', '.jpeg', '.png']:
                    process_file(p, FOOD_TYPE_DIR, cat_name, "FoodTypeOnly", "Food-101", cat_name)
    else:
        print("Food-101 directory not found.")

    # Write Manifest
    with open(MANIFEST_PATH, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=[
            "filename", "source_dataset", "original_label", "mapped_label", "food_category", "sha256"
        ])
        writer.writeheader()
        writer.writerows(manifest_rows)

    # Write Report
    report_lines = [
        "Dataset Audit Report - FoodShareAI (V2)",
        "=======================================",
        f"TOTAL_IMAGES = {stats['TOTAL_IMAGES']}",
        f"FRESH = {stats['FRESH']}",
        f"SPOILED = {stats['SPOILED']}",
        f"FOOD_TYPE_ONLY = {stats['FOOD_TYPE_ONLY']}",
        f"INVALID = {stats['INVALID']}",
        f"DUPLICATES_REMOVED = {stats['DUPLICATES_REMOVED']}",
        "",
        "FOOD CATEGORY COUNTS (Fresh / Spoiled folders):",
        "-----------------------------------------------"
    ]
    
    for cat in CATEGORIES_TO_TRACK:
        f_count = cat_counts["Fresh"][cat]
        s_count = cat_counts["Spoiled"][cat]
        report_lines.append(f"{cat:<20}: {f_count} Fresh, {s_count} Spoiled")
        
    report_content = "\n".join(report_lines)
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        f.write(report_content)
        
    print("\n" + report_content)

if __name__ == "__main__":
    main()
