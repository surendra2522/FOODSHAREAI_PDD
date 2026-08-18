# 🍲 FoodShare AI — On-Device Food Waste Reduction & Redistribution Platform

> An Android application designed to streamline surplus food donation using **100% Privacy-Preserving, On-Device TensorFlow Lite AI Inference**, real-time OpenStreetMap Logistics routing, and offline-first Room database synchronization.

---

## 📸 Core Features & AI Pipeline

### 🔍 1. On-Device AI Food Verification Pipeline
- **Zero Cloud API / Zero API Keys**: Operates completely offline without sending user photos to external servers or cloud APIs.
- **Two-Stage TensorFlow Lite Pipeline**:
  - **Stage 1 (Food vs. Non-Food Detection)**: Evaluates image input against a MobileNet Float32 TFLite classification model. Rejects non-food items (e.g., QR codes, printed paper, documents, electronic devices) with `VerificationStatus.NON_FOOD`.
  - **Stage 2 (Freshness Assessment)**: Analyzes visual condition (Fresh, Acceptable, Questionable, Spoiled) using on-device feature extraction.
- **Strict Business Authority Rules**:
  - Automatically disables the **"Next Step"** button if non-food, low image quality, or visual spoilage is detected (`canPublish = false`).
  - Calculates surplus meal quantities automatically while ensuring donation safety.

---

### 🗺️ 2. Logistics & Live Pickup Tracking
- **OpenStreetMap & osmdroid Integration**: Displays live interactive maps without reliance on paid proprietary map APIs.
- **Automated Route Calculation**: Shows donor pickup points, designated NGO drop-off centers, and optimal navigation paths.
- **Status Timeline**: Tracks donation milestones from *Created* → *Accepted* → *Picked Up* → *Delivered*.

---

### 📊 3. Impact Analytics & Verification History
- **Environmental Impact Metrics**: Calculates food saved in kilograms, equivalent CO₂ offsets, and total community beneficiaries fed.
- **Local Room Database History**: Offline storage for all completed donations, AI verification logs, and audit reports.

---

## 🛠️ Architecture & Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin (100%) |
| **UI Framework** | Jetpack Compose + Material 3 Design System |
| **On-Device AI Engine** | TensorFlow Lite (`org.tensorflow:tensorflow-lite:2.14.0`), TensorFlow Lite Support (`0.4.4`) |
| **Maps & Logistics** | OpenStreetMap Android SDK (`org.osmdroid:osmdroid-android:6.1.18`) |
| **Local Database** | Android Room Persistent Library with Coroutines & Flow |
| **Architecture Pattern** | MVVM (Model-View-ViewModel) + Clean Data Layer |
| **Image Processing** | Coil for Compose + Android Graphics Matrix scaling |

---

## ⚙️ On-Device AI Model Details

```text
MODEL FILE:           app/src/main/assets/models/food_classifier.tflite
FRESHNESS MODEL:      app/src/main/assets/models/food_freshness.tflite
MODEL FORMAT:         TensorFlow Lite FlatBuffer (.tflite, uncompressed APK packaging)
INPUT TENSOR:         [1, 224, 224, 3] (Float32, RGB Normalized to [-1.0, 1.0])
OUTPUT TENSOR:        [1, 1000] (Float32 probabilities across ImageNet synset classes)
LABEL MAPPING:        app/src/main/assets/models/food_labels.txt (1,000 class labels)
MEMORY MAPPING:       MappedByteBuffer via Android AssetManager (noCompress += "tflite")
```

---

## 🚀 Building & Running Locally

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: Java 17
- **Android SDK**: `compileSdk = 35`, `minSdk = 24`
- **Device**: Android 7.0+ physical device or ARM/x86_64 emulator

### Build Steps

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/YourUsername/FoodShareAI.git
   cd FoodShareAI
   ```

2. **Configure Local Properties**:
   Create or update `local.properties` in the project root directory:
   ```properties
   sdk.dir=C:/Users/YOUR_USERNAME/AppData/Local/Android/Sdk
   ```

3. **Clean & Build the Debug APK**:
   ```bash
   ./gradlew clean assembleDebug
   ```

4. **Install on Connected Device**:
   ```bash
   ./gradlew installDebug
   ```

---

## 🛡️ License

This project is open-source under the **MIT License**.
See `LICENSE` for details.
