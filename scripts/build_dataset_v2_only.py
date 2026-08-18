#!/usr/bin/env python3
"""
FoodShareAI - Dataset V2 Builder (Prepared Foods Only)
Builds `freshness_dataset_v2` using legitimate publicly available datasets.
Filters out raw fruits/vegetables and keeps only prepared foods (e.g. bread, dairy, etc).
"""
import os
import shutil
import hashlib
import csv
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RAW_DIR = ROOT / "freshness_downloads_temp"
V2_DIR = ROOT / "freshness_dataset_v2"
MANIFEST_PATH = ROOT / "manifest_v2.csv"

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

def is_valid_image(path):
    try:
        with Image.open(path) as img:
            w, h = img.size
            if w < 32 or h < 32: return False
            # Check for extreme aspect ratio (non-food)
            ratio = max(w/h, h/w)
            if ratio > 4.0: return False
            img.verify()
        return True
    except Exception:
        return False

def build_v2():
    print("="*60)
    print("Building freshness_dataset_v2 (Prepared Foods Only)")
    print("="*60)

    if V2_DIR.exists():
        shutil.rmtree(V2_DIR)
    
    for cls in ("Fresh", "Spoiled"):
        (V2_DIR / cls).mkdir(parents=True, exist_ok=True)

    manifest_records = []
    seen_hashes = set()
    counts = {"Fresh": 0, "Spoiled": 0}
    stats = {
        "total_scanned": 0,
        "duplicates_removed": 0,
        "invalid_removed": 0,
        "raw_produce_removed": 0,
        "categories": set(),
        "sources": set()
    }

    # 1. Process Kaggle dataset (already downloaded as kaggle_fresh_spoiled)
    kaggle_dir = RAW_DIR / "kaggle_fresh_spoiled" / "dataset"
    if kaggle_dir.exists():
        for root, dirs, files in os.walk(kaggle_dir):
            folder_name = Path(root).name.lower()
            
            # Map labels
            mapped_label = None
            if "fresh" in folder_name:
                mapped_label = "Fresh"
            elif "spoiled" in folder_name or "rotten" in folder_name or "stale" in folder_name or "moldy" in folder_name:
                mapped_label = "Spoiled"
            
            if not mapped_label:
                continue
                
            # Filter out fruits and vegetables (raw produce)
            if "fruit" in folder_name or "vegetable" in folder_name or "apple" in folder_name or "banana" in folder_name or "orange" in folder_name or "tomato" in folder_name or "potato" in folder_name or "cucumber" in folder_name:
                for f in files: stats["raw_produce_removed"] += 1
                continue

            for file in files:
                if not file.lower().endswith(('.jpg', '.jpeg', '.png', '.webp')):
                    continue
                    
                stats["total_scanned"] += 1
                src_path = Path(root) / file
                
                if not is_valid_image(src_path):
                    stats["invalid_removed"] += 1
                    continue
                    
                h = sha256(src_path)
                if h in seen_hashes:
                    stats["duplicates_removed"] += 1
                    continue
                seen_hashes.add(h)
                
                # Determine category
                category = folder_name.replace("fresh_", "").replace("spoiled_", "").replace("moldy_", "").replace("rotten_", "").replace("stale_", "")
                
                # Copy file
                dest_filename = f"{mapped_label.lower()}_{category}_{h[:12]}{src_path.suffix.lower()}"
                dest_path = V2_DIR / mapped_label / dest_filename
                shutil.copy2(src_path, dest_path)
                
                counts[mapped_label] += 1
                stats["categories"].add(category)
                stats["sources"].add("Kaggle Fresh and Spoiled Food Image Dataset")
                
                manifest_records.append({
                    "filename": dest_filename,
                    "source_dataset": "Kaggle Fresh and Spoiled Food Image Dataset",
                    "source_url": "https://www.kaggle.com/datasets/maheen00shahid/fresh-and-spoiled-food-image-dataset",
                    "original_label": folder_name,
                    "mapped_label": mapped_label,
                    "food_category": category,
                    "sha256": h
                })

    # Write Manifest
    if manifest_records:
        with open(MANIFEST_PATH, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=[
                "filename", "source_dataset", "source_url", "original_label",
                "mapped_label", "food_category", "sha256"
            ])
            writer.writeheader()
            writer.writerows(manifest_records)

    # Report
    print(f"\nReport:")
    print(f"Total images scanned: {stats['total_scanned']}")
    print(f"Fresh count: {counts['Fresh']}")
    print(f"Spoiled count: {counts['Spoiled']}")
    print(f"Food categories: {', '.join(sorted(stats['categories']))}")
    print(f"Source datasets: {', '.join(sorted(stats['sources']))}")
    print(f"Number removed as duplicates: {stats['duplicates_removed']}")
    print(f"Number removed as invalid/corrupt: {stats['invalid_removed']}")
    print(f"Number removed as raw produce (fruits/veg): {stats['raw_produce_removed']}")
    print(f"\nManifest written to {MANIFEST_PATH.name}")
    print("WARNING: Mendeley datasets (Rice/Parotta) require manual authentication to download.")
    print("Dataset V2 creation complete. No training was performed.")

if __name__ == "__main__":
    build_v2()
