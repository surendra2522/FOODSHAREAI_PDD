plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)

  id("com.google.gms.google-services")
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.surendra.foodshareai"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keystoreFile = file(keystorePath)
      if (keystoreFile.exists()) {
        storeFile = keystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      if (signingConfigs.getByName("release").storeFile?.exists() == true) {
        signingConfig = signingConfigs.getByName("release")
      }
    }

    debug {
      // Uses Android Studio default debug keystore
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  androidResources {
    noCompress += "tflite"
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

secrets {
  propertiesFileName = "local.properties"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("sdk.dir")
}

dependencies {

  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.storage)
  implementation(libs.firebase.messaging)

  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)

  implementation(libs.androidx.core.ktx)

  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.navigation.compose)

  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)

  implementation(libs.coil.compose)

  implementation(libs.converter.moshi)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  implementation(libs.logging.interceptor)

  implementation(libs.moshi.kotlin)

  implementation(libs.okhttp)

  implementation(libs.play.services.location)
  implementation(libs.osmdroid.android)

  implementation(libs.retrofit)
  implementation("com.google.guava:guava:33.0.0-android")
  implementation("org.tensorflow:tensorflow-lite:2.14.0")
  implementation("org.tensorflow:tensorflow-lite-support:0.4.4")


  testImplementation(libs.junit)
  testImplementation(libs.androidx.core)

  testImplementation(libs.kotlinx.coroutines.test)

  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)

  // Shared test dependencies
  val testJunit = libs.androidx.junit
  val composeTestJunit = libs.androidx.compose.ui.test.junit4

  testImplementation(testJunit)
  testImplementation(composeTestJunit)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(testJunit)
  androidTestImplementation(composeTestJunit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  ksp(libs.androidx.room.compiler)
  ksp(libs.moshi.kotlin.codegen)
}