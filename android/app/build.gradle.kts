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

android {
    namespace = "id.orvyn.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.orvyn.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
        release {
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
    buildFeatures { compose = true }
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
    "id.orvyn.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", // AndroidX-internal, app-private
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
