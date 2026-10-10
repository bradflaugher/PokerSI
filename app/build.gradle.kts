plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionCodeFromEnvironment = System.getenv("POKERSI_VERSION_CODE") ?: "1"
require(versionCodeFromEnvironment.matches(Regex("[1-9]\\d*"))) {
    "POKERSI_VERSION_CODE must be a positive integer"
}

// Release signing is all-or-nothing: set all four variables or none.
val signingEnvironmentVariables = listOf(
    "POKERSI_KEYSTORE_PATH",
    "POKERSI_STORE_PASSWORD",
    "POKERSI_KEY_ALIAS",
    "POKERSI_KEY_PASSWORD",
)
val configuredSigningVariables = signingEnvironmentVariables.filter { !System.getenv(it).isNullOrEmpty() }
if (configuredSigningVariables.isNotEmpty() && configuredSigningVariables.size != signingEnvironmentVariables.size) {
    val missing = signingEnvironmentVariables - configuredSigningVariables.toSet()
    throw GradleException("Release signing is partially configured. Missing: ${missing.joinToString()}")
}
val releaseSigningEnabled = configuredSigningVariables.size == signingEnvironmentVariables.size

// `-PskipNative` builds without llama.cpp (unit tests, lint and screenshots
// don't need it, and it is the slow part of the build). The app then runs
// with only the built-in House Bot.
val skipNative = providers.gradleProperty("skipNative").isPresent

android {
    namespace = "com.bradflaugher.pokersi"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.bradflaugher.pokersi"
        // The newest API the code uses (AGSL RuntimeShader); see AGENTS.md.
        minSdk = 33
        targetSdk = 37
        versionCode = versionCodeFromEnvironment.toInt()
        versionName = System.getenv("POKERSI_VERSION_NAME") ?: "dev"

        if (!skipNative) {
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=Release"
                    arguments += "-DLLAMA_CURL=OFF"
                    arguments += "-DLLAMA_OPENSSL=OFF"
                    arguments += "-DLLAMA_BUILD_COMMON=ON"
                    arguments += "-DLLAMA_BUILD_TESTS=OFF"
                    arguments += "-DLLAMA_BUILD_EXAMPLES=OFF"
                    arguments += "-DLLAMA_BUILD_TOOLS=OFF"
                    arguments += "-DLLAMA_BUILD_SERVER=OFF"
                    arguments += "-DLLAMA_BUILD_APP=OFF"
                    arguments += "-DLLAMA_SUBPROCESS=OFF"
                    arguments += "-DGGML_LLAMAFILE=OFF"
                    arguments += "-DGGML_NATIVE=OFF"
                    arguments += "-DGGML_OPENMP=OFF"
                    arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                    // Shared libs + dynamic CPU backends: one libggml-cpu-<variant>.so
                    // per ARM feature level (dotprod, i8mm, SVE...), the best one is
                    // picked at runtime. Same setup as LM Playground.
                    arguments += "-DBUILD_SHARED_LIBS=ON"
                    arguments += "-DGGML_BACKEND_DL=ON"
                    arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                }
            }
            ndk {
                abiFilters += setOf("arm64-v8a", "x86_64")
            }
        }
    }

    if (!skipNative) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "4.1.2"
            }
        }
    }

    androidResources {
        localeFilters += listOf("en")
    }

    signingConfigs {
        if (releaseSigningEnabled) {
            create("release") {
                storeFile = file(System.getenv("POKERSI_KEYSTORE_PATH"))
                storePassword = System.getenv("POKERSI_STORE_PASSWORD")
                keyAlias = System.getenv("POKERSI_KEY_ALIAS")
                keyPassword = System.getenv("POKERSI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Play symbolicates native crashes from the bundle's symbol table.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ggml loads its CPU backend variants with dlopen from nativeLibraryDir,
    // so the libraries must be extracted on install.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric screenshot tests (ui/) need the merged resources: fonts, strings.
        unitTests.isIncludeAndroidResources = true
    }

    // Play reads the dependency list from the bundle; the sideloaded APK doesn't
    // need Google's encrypted copy of it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

kotlin {
    jvmToolchain(21)
}

// Show failing assertions in the build log (CI included), not just in the HTML report.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // Screenshot tests (Roborazzi + Robolectric native graphics) write PNGs
    // under app/build/outputs/roborazzi/. See ui/pixel/ScreenshotRenderer in test.
    systemProperty("roborazzi.test.record", "true")
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
    // Store screenshots (StoreScreenshotTest) render only with -Pstore or
    // POKERSI_STORE_SHOTS set, into <repo>/build/screenshots/; see
    // tools/screenshots/shoot.py. Plain `test` skips them.
    if (providers.gradleProperty("store").isPresent || providers.environmentVariable("POKERSI_STORE_SHOTS").isPresent) {
        systemProperty("pokersi.storeShotsDir", rootProject.layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    }
    maxHeapSize = "4g"
    // Robolectric reaches into FileDescriptor internals (fonts, bitmaps) on JDK 17+.
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.work.runtime)
    implementation(libs.documentfile)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.compose.ui.test.junit4)
}
