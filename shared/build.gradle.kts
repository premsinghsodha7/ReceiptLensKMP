plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ReceiptLensCore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies { }
    }
}

android {
    namespace = "com.premsingh.receiptlens.shared"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
}
