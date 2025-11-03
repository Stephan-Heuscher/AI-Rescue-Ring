import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Load version properties
val versionProps = Properties().apply {
    val versionFile = file("${project.rootDir}/version.properties")
    if (versionFile.exists()) {
        FileInputStream(versionFile).use { load(it) }
    }
}

fun getVersionCode(): Int = versionProps.getProperty("VERSION_CODE", "1").toInt()
fun getVersionName(): String {
    val major = versionProps.getProperty("VERSION_MAJOR", "1")
    val minor = versionProps.getProperty("VERSION_MINOR", "0")
    val patch = versionProps.getProperty("VERSION_PATCH", "0")
    return "$major.$minor.$patch"
}

android {
    namespace = "ch.heuscher.back_home_dot"
    compileSdk = 36

    defaultConfig {
        applicationId = "ch.heuscher.back_home_dot"
        minSdk = 26
        targetSdk = 36
        versionCode = getVersionCode()
        versionName = getVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    signingConfigs {
        // TODO: Keystore für Release-Signierung erstellen
        // create {
        //     storeFile = file("path/to/your/keystore.jks")
        //     storePassword = "your-keystore-password"
        //     keyAlias = "your-key-alias"
        //     keyPassword = "your-key-password"
        // }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

// Task to increment version code and patch version for release builds
tasks.register("incrementVersionForRelease") {
    doLast {
        val versionFile = file("${project.rootDir}/version.properties")
        val props = Properties()

        if (versionFile.exists()) {
            FileInputStream(versionFile).use { props.load(it) }
        }

        // Increment version code
        val currentVersionCode = props.getProperty("VERSION_CODE", "1").toInt()
        val newVersionCode = currentVersionCode + 1
        props.setProperty("VERSION_CODE", newVersionCode.toString())

        // Increment patch version
        val currentPatch = props.getProperty("VERSION_PATCH", "0").toInt()
        val newPatch = currentPatch + 1
        props.setProperty("VERSION_PATCH", newPatch.toString())

        // Save updated properties
        versionFile.outputStream().use { props.store(it, "Version incremented for release build") }

        // Calculate new version name
        val major = props.getProperty("VERSION_MAJOR", "1")
        val minor = props.getProperty("VERSION_MINOR", "0")
        val newVersionName = "$major.$minor.$newPatch"

        println("Version incremented: Code=$newVersionCode, Name=$newVersionName")
    }
}

// Configure release build to increment version
afterEvaluate {
    tasks.named("assembleRelease").configure {
        dependsOn("incrementVersionForRelease")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}