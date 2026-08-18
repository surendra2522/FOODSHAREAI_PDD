import os
import sys
import shutil
import hashlib
import tensorflow as tf
from pathlib import Path

ROOT_DIR = Path(r'c:\Users\jangi\Downloads\FoodShareAI-main (1)\FoodShareAI-main')
ASSETS_DIR = ROOT_DIR / 'app/src/main/assets/models'
PROD_MODEL_PATH = ASSETS_DIR / 'food_freshness.tflite'
BACKUP_MODEL_PATH = ASSETS_DIR / 'food_freshness_production_backup.tflite'
V3_MODEL_PATH = ROOT_DIR / 'food_freshness_v3.tflite'
LABELS_PATH = ASSETS_DIR / 'freshness_labels.txt'

print("==================================================")
print("1. CREATING BACKUP OF EXISTING PRODUCTION MODEL")
print("==================================================")

if not PROD_MODEL_PATH.exists():
    print(f"ERROR: {PROD_MODEL_PATH} does not exist.")
    sys.exit(1)

prod_bytes = PROD_MODEL_PATH.read_bytes()
prod_hash = hashlib.sha256(prod_bytes).hexdigest()
prod_size = len(prod_bytes)

print(f"Existing Production Model:")
print(f"  Size:   {prod_size} bytes")
print(f"  SHA256: {prod_hash}")

# Create Backup
shutil.copy2(PROD_MODEL_PATH, BACKUP_MODEL_PATH)
backup_bytes = BACKUP_MODEL_PATH.read_bytes()
backup_hash = hashlib.sha256(backup_bytes).hexdigest()
backup_size = len(backup_bytes)

print(f"\nBackup Model Created:")
print(f"  Path:   {BACKUP_MODEL_PATH}")
print(f"  Size:   {backup_size} bytes")
print(f"  SHA256: {backup_hash}")

if backup_hash != prod_hash:
    print("ERROR: Backup hash mismatch!")
    sys.exit(1)
print("BACKUP VERIFIED SUCCESSFUL!")

print("\n==================================================")
print("2. REPLACING PRODUCTION MODEL WITH V3 TFLITE")
print("==================================================")

if not V3_MODEL_PATH.exists():
    print(f"ERROR: {V3_MODEL_PATH} does not exist.")
    sys.exit(1)

v3_bytes = V3_MODEL_PATH.read_bytes()
v3_hash = hashlib.sha256(v3_bytes).hexdigest()
v3_size = len(v3_bytes)

print(f"V3 Model Source:")
print(f"  Size:   {v3_size} bytes")
print(f"  SHA256: {v3_hash}")

# Overwrite food_freshness.tflite with V3
shutil.copy2(V3_MODEL_PATH, PROD_MODEL_PATH)

active_bytes = PROD_MODEL_PATH.read_bytes()
active_hash = hashlib.sha256(active_bytes).hexdigest()
active_size = len(active_bytes)

print(f"\nActive Production Asset Updated:")
print(f"  Path:   {PROD_MODEL_PATH}")
print(f"  Size:   {active_size} bytes")
print(f"  SHA256: {active_hash}")

if active_hash != v3_hash or active_hash != "cd8d2ae4a79b509e020e20b71430e67bdb52961dbf41f66fb9529cb67934e695":
    print("ERROR: Active asset hash mismatch!")
    sys.exit(1)
print("ACTIVE ASSET VERIFICATION SUCCESSFUL!")

print("\n==================================================")
print("3. VERIFYING TFLITE TENSORS & LABELS")
print("==================================================")

interpreter = tf.lite.Interpreter(model_path=str(PROD_MODEL_PATH))
interpreter.allocate_tensors()

in_det = interpreter.get_input_details()[0]
out_det = interpreter.get_output_details()[0]

in_shape = list(in_det['shape'])
out_shape = list(out_det['shape'])

labels = LABELS_PATH.read_text(encoding='utf-8').splitlines()

print(f"Active TFLite Input Shape:  {in_shape}")
print(f"Active TFLite Output Shape: {out_shape}")
print(f"Labels ({len(labels)}):")
for i, l in enumerate(labels):
    print(f"  CLASS {i} = {l}")

print("\n==================================================")
print("DEPLOYMENT VERIFICATION SUMMARY")
print("==================================================")
print(f"V3_ANDROID_MODEL_DEPLOYED = YES")
print(f"BACKUP_CREATED = YES")
print(f"BACKUP_HASH = {backup_hash}")
print(f"ACTIVE_TFLITE_HASH = {active_hash}")
print(f"ACTIVE_TFLITE_SIZE = {active_size} bytes")
print(f"ANDROID_CODE_MODIFIED = NO")
