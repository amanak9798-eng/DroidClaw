plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.droidclaw.llm"
    compileSdk = 36

    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 29
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                arguments(
                    "-DGGML_VULKAN=OFF",
                    "-DGGML_OPENCL=OFF",
                    "-DLLAMA_BUILD_SERVER=ON",
                    "-DLLAMA_BUILD_COMMON=ON",
                    "-DLLAMA_BUILD_TOOLS=ON",
                    "-DLLAMA_OPENSSL=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DGGML_OPENMP=OFF"
                )
            }
        }
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Native executables are now packaged via jniLibs (see buildLlamaServer task below).
    // No longer placed in assets.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// ── Build llama-server executable + copy to jniLibs ─────────────
// AGP only builds shared library targets via CMake. We invoke Ninja
// manually to build the llama-server executable, then rename it to
// libllama-server.so and place it in jniLibs/arm64-v8a/ so that:
//   1) Android installs it into nativeLibraryDir at install time
//   2) The binary is executable (app data dir is noexec on Android 10+)

android.libraryVariants.all {
    val variantName = name
    val capitalizedVariant = variantName.replaceFirstChar { it.uppercase() }

    val cxxBaseDir = file(".cxx")

    val buildServerTask = tasks.register("buildLlamaServer${capitalizedVariant}") {
        doLast {
            val buildDir = fileTree(cxxBaseDir) {
                include("**/$variantName/**/arm64-v8a/build.ninja")
            }.files.firstOrNull()?.parentFile

            if (buildDir == null) {
                logger.warn("Could not find CMake build directory for $variantName. Run assembleDebug first to configure CMake.")
                return@doLast
            }

            logger.lifecycle("Building llama-server in: ${buildDir.path}")

            val sdkDir = android.sdkDirectory
            val ninja = File(sdkDir, "cmake/3.22.1/bin/ninja.exe")
                .takeIf { it.exists() }
                ?: File(sdkDir, "cmake/3.22.1/bin/ninja")
                    .takeIf { it.exists() }
                ?: throw GradleException("Ninja not found in SDK cmake directory")

            val runNinja = { action: String ->
                val process = ProcessBuilder(ninja.absolutePath, "-C", buildDir.absolutePath, action).inheritIO().start()
                val exitCode = process.waitFor()
                if (exitCode != 0) throw GradleException("Ninja $action failed with exit code $exitCode")
            }

            // 1. Build and copy the baseline version
            runNinja("llama-server")

            var serverBinary: File? = null
            cxxBaseDir.walkTopDown().forEach { file ->
                if (file.isFile && (file.name == "llama-server" || file.name == "llama-server.exe")) {
                    if (file.absolutePath.contains("arm64-v8a") && file.parentFile?.name == "bin") {
                        serverBinary = file
                    }
                }
            }

            val targetDir = rootProject.file("app/src/main/jniLibs/arm64-v8a")
            targetDir.mkdirs()

            if (serverBinary != null && serverBinary!!.exists()) {
                serverBinary!!.copyTo(File(targetDir, "libllama-server.so"), overwrite = true)
                logger.lifecycle("Copied baseline llama-server (${serverBinary!!.length() / 1024}KB) -> jniLibs/arm64-v8a/libllama-server.so")
            } else {
                logger.warn("baseline llama-server binary not found in .cxx tree")
            }

            // 2. Clear old CMake cache / flags to rebuild with dotprod
            logger.lifecycle("Applying dotprod flags and rebuilding...")
            // Modifying the CMakeCache.txt to inject armv8.4-a+dotprod flags
            val cmakeCacheFile = File(buildDir, "CMakeCache.txt")
            if (cmakeCacheFile.exists()) {
                val cacheLines = cmakeCacheFile.readLines().toMutableList()
                val newFlags = "-march=armv8.4-a+dotprod"
                for (i in cacheLines.indices) {
                    if (cacheLines[i].startsWith("CMAKE_CXX_FLAGS:")) {
                        // Append dotprod flag
                        cacheLines[i] = cacheLines[i] + " " + newFlags
                    } else if (cacheLines[i].startsWith("CMAKE_C_FLAGS:")) {
                        cacheLines[i] = cacheLines[i] + " " + newFlags
                    }
                }
                cmakeCacheFile.writeText(cacheLines.joinToString("\n"))
            }

            // Force recompilation of objects
            runNinja("clean")
            runNinja("llama-server")

            if (serverBinary != null && serverBinary!!.exists()) {
                serverBinary!!.copyTo(File(targetDir, "libllama-server-dotprod.so"), overwrite = true)
                logger.lifecycle("Copied dotprod llama-server (${serverBinary!!.length() / 1024}KB) -> jniLibs/arm64-v8a/libllama-server-dotprod.so")
            } else {
                logger.warn("dotprod llama-server binary not found in .cxx tree")
            }

            // Clean again to leave the tree in a pristine state for the next AGP sync
            runNinja("clean")
        }
    }

    // Run after CMake builds the shared libraries, before JNI merge
    tasks.matching { it.name == "buildCMake${capitalizedVariant}[arm64-v8a]" }.configureEach {
        finalizedBy(buildServerTask)
    }
    tasks.matching { it.name == "merge${capitalizedVariant}JniLibFolders" }.configureEach {
        dependsOn(buildServerTask)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
