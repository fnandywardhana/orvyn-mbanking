import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

// Release signing key lives outside the repo (story S0.3: one key for every version).
val signing = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use(::load)
}

// Story S2.0: Firebase settings come from ../secrets/google-services.json (kept out of git) as the
// string resources FirebaseApp reads at start-up, so the google-services plugin isn't needed. No file, no push.
@Suppress("UNCHECKED_CAST")
val firebase = rootProject.file("../secrets/google-services.json").takeIf { it.exists() }?.let { f ->
    val json = groovy.json.JsonSlurper().parse(f) as Map<String, Any>
    val project = json["project_info"] as Map<String, String>
    val client = (json["client"] as List<Map<String, Any>>).first {
        ((it["client_info"] as Map<String, Any>)["android_client_info"] as Map<String, String>)["package_name"] == "id.orvyn.app"
    }
    mapOf(
        "google_app_id" to (client["client_info"] as Map<String, String>)["mobilesdk_app_id"]!!,
        "google_api_key" to (client["api_key"] as List<Map<String, String>>).first()["current_key"]!!,
        "gcm_defaultSenderId" to project["project_number"]!!,
        "project_id" to project["project_id"]!!,
    )
}

android {
    namespace = "id.orvyn.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.orvyn.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.6.0"
        firebase?.forEach { (k, v) -> resValue("string", k, v) }
    }

    signingConfigs {
        create("release") {
            if (signing.isNotEmpty()) {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Reaches the local dev server through `adb reverse tcp:8790 tcp:8790`.
            buildConfigField("String", "API_URL", "\"http://localhost:8790/api\"")
        }
        release {
            buildConfigField("String", "API_URL", "\"https://orvyn.delvault.uk/api\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.biometric:biometric:1.1.0")
    // biometric 1.1.0 pulls an old fragment library that crashes the photo picker ("lower 16 bits for requestCode")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    // QR: ZXing reads and draws codes fully on the phone (no Google services, no network; ADR 0002)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.firebase:firebase-messaging:25.1.3")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.74.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.74.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Story S0.6 / ADR 0002: the APK may only ask for these permissions. Anything else fails the build.
val allowedPermissions = setOf(
    "android.permission.INTERNET",
    "android.permission.CAMERA",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.USE_BIOMETRIC",
    "android.permission.USE_FINGERPRINT", // added by androidx.biometric for Android 8–8.1 phones
    "id.orvyn.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", // AndroidX-internal, app-private
    // Added by Firebase Cloud Messaging (story S2.0). None of them reads anything about the person or the phone.
    "com.google.android.c2dm.permission.RECEIVE", // receive pushes
    "android.permission.WAKE_LOCK", // keep the phone awake long enough to show one
    "android.permission.ACCESS_NETWORK_STATE", // wait for a connection before fetching the token
)

androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
        val check = tasks.register("check${variant.name.replaceFirstChar(Char::uppercase)}Permissions") {
            inputs.file(manifest)
            doLast {
                val requested = Regex("""<uses-permission(?:-sdk-23)?[^>]*android:name="([^"]+)"""")
                    .findAll(manifest.get().asFile.readText()).map { it.groupValues[1] }.toSet()
                val extra = requested - allowedPermissions
                if (extra.isNotEmpty()) throw GradleException("Permission not approved (PRD 7.4): ${extra.joinToString()}")
            }
        }
        tasks.matching { it.name == "assemble${variant.name.replaceFirstChar(Char::uppercase)}" }.configureEach { dependsOn(check) }
    }
}
