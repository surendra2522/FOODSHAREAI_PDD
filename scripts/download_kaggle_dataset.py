#!/usr/bin/env python3
import os
import urllib.request
import zipfile
from pathlib import Path

WORKSPACE_ROOT = Path(__file__).resolve().parent.parent
DOWNLOAD_DIR = WORKSPACE_ROOT / "freshness_downloads_temp"
ZIP_PATH = DOWNLOAD_DIR / "fresh_spoiled_food.zip"
EXTRACT_DIR = DOWNLOAD_DIR / "kaggle_fresh_spoiled"

def download_and_extract():
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
    EXTRACT_DIR.mkdir(parents=True, exist_ok=True)
    
    url = "https://www.kaggle.com/api/v1/datasets/download/maheen00shahid/fresh-and-spoiled-food-image-dataset"
    print(f"Downloading Kaggle Fresh & Spoiled Food dataset (597 MB) from {url}...")
    
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    with urllib.request.urlopen(req, timeout=120) as resp, open(ZIP_PATH, "wb") as out_f:
        shutil_buf_size = 1024 * 1024
        downloaded = 0
        while True:
            chunk = resp.read(shutil_buf_size)
            if not chunk:
                break
            out_f.write(chunk)
            downloaded += len(chunk)
            print(f"Downloaded {downloaded / (1024*1024):.1f} MB...", end="\r")
            
    print(f"\nDownload finished! Extracting to {EXTRACT_DIR}...")
    with zipfile.ZipFile(ZIP_PATH, 'r') as zip_ref:
        zip_ref.extractall(EXTRACT_DIR)
        
    print("Extraction complete!")
    
    # Inspect contents
    image_count = 0
    subdirs = []
    for root, dirs, files in os.walk(EXTRACT_DIR):
        for d in dirs:
            subdirs.append(os.path.join(root, d))
        for f in files:
            if f.lower().endswith(('.jpg', '.jpeg', '.png', '.webp')):
                image_count += 1
                
    print(f"Extracted Subdirectories: {len(subdirs)}")
    print(f"Extracted Total Images: {image_count}")
    
    # List top level extracted folders
    print("\nTop-level extracted contents:")
    for p in EXTRACT_DIR.iterdir():
        print("  -", p.name, "(dir)" if p.is_dir() else "(file)")

if __name__ == "__main__":
    download_and_extract()
