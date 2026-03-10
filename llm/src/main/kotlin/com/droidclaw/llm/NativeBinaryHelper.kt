package com.droidclaw.llm

import android.content.Context
import java.io.File

/**
 * Resolves native executables packaged as .so files inside the APK's jniLibs.
 *
 * Android 10+ blocks executing binaries from the app data directory (noexec mount).
 * The ONLY directory where executables can run is [Context.getApplicationInfo.nativeLibraryDir].
 *
 * Convention: a binary named "foo" is packaged as "libfoo.so" in jniLibs/{abi}/.
 * At install time Android copies it to nativeLibraryDir and it is already executable.
 */
object NativeBinaryHelper {

    /**
     * Returns the [File] pointing to the native executable inside nativeLibraryDir.
     *
     * @param context  Application or Service context
     * @param binaryName  The logical binary name (e.g. "llama-server").
     *                    The corresponding file must be packaged as "lib{binaryName}.so"
     *                    inside jniLibs/{abi}/.
     * @throws IllegalStateException if the binary is not found in nativeLibraryDir.
     */
    fun findBinary(context: Context, binaryName: String): File {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        // Detect hardware capabilities to choose the most optimized binary
        val features = getCPUFeatures()
        val hasDotProd = features.contains("dotprod") || features.contains("asimddp")
        
        val actualBinaryName = if (binaryName == "llama-server" && hasDotProd) {
            android.util.Log.i("NativeBinaryHelper", "CPU supports dotprod. Selecting optimized llama-server binary.")
            "llama-server-dotprod"
        } else {
            binaryName
        }

        val soName = "lib${actualBinaryName}.so"
        val binaryFile = File(nativeDir, soName)

        if (!binaryFile.exists()) {
            val available = nativeDir.listFiles()?.map { it.name }?.joinToString() ?: "empty"
            // Fallback to baseline if optimized not found
            if (actualBinaryName != binaryName) {
                android.util.Log.w("NativeBinaryHelper", "Optimized binary '$soName' not found, falling back to baseline '$binaryName'.")
                // Recursively call with the baseline name, BUT we need to ensure the recursive call
                // doesn't just resolve back to the optimized one. We can do this by appending a flag or 
                // simply resolving it directly here to avoid recursion entirely.
                val fallbackSoName = "lib${binaryName}.so"
                val fallbackBinaryFile = File(nativeDir, fallbackSoName)
                if (fallbackBinaryFile.exists()) {
                    android.util.Log.d("NativeBinaryHelper", "Resolved fallback $binaryName -> ${fallbackBinaryFile.absolutePath}")
                    return fallbackBinaryFile
                }
            }
            throw IllegalStateException(
                "Native binary '$soName' (and fallback) not found in ${nativeDir.absolutePath}. " +
                    "Available: [$available]. " +
                    "Ensure the binary is placed in jniLibs/{abi}/."
            )
        }

        android.util.Log.d("NativeBinaryHelper", "Resolved $actualBinaryName -> ${binaryFile.absolutePath}")
        return binaryFile
    }

    /**
     * Reads the /proc/cpuinfo file and returns the line starting with 'Features :' that
     * containing the available CPU features
     */
    private fun getCPUFeatures(): String {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            cpuInfo.substringAfter("Features").substringAfter(":").substringBefore("\n").trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Legacy bridge: same signature as old extractBinary() so callers compile without changes.
     * The [targetDir] parameter is ignored — binaries always live in nativeLibraryDir.
     */
    @Suppress("UNUSED_PARAMETER")
    fun extractBinary(context: Context, assetName: String, targetDir: File): File {
        return findBinary(context, assetName)
    }
}
