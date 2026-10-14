// Standalone Vocal Remover prototype. Model weights are fetched on-device only after
// the user explicitly asks; selected song audio is never uploaded.
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Fetches the official sherpa-onnx Android FFI packages, not the upstream Android JNI AAR.
 * The regular AAR does not contain libsherpa-onnx-c-api.so, which source separation needs.
 * Pub's immutable archive hashes are pinned below and every packaged ELF is ABI-checked.
 */
abstract class DownloadSherpaRuntime : DefaultTask() {
    @get:OutputDirectory
    abstract val runtimeDirectory: DirectoryProperty

    private data class RuntimePackage(
        val packageName: String,
        val abi: String,
        val archiveSha256: String,
    )

    @TaskAction
    fun downloadAndExtract() {
        temporaryDir.mkdirs()
        val output = runtimeDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val packages = listOf(
            RuntimePackage(
                packageName = "sherpa_onnx_android_arm64",
                abi = "arm64-v8a",
                archiveSha256 = "29db5572735afb1bc29f0f95ab160f7fb38b4b3f926f48e9113457fd740264f4",
            ),
            RuntimePackage(
                packageName = "sherpa_onnx_android_armeabi",
                abi = "armeabi-v7a",
                archiveSha256 = "fe81837bd12f67d136a456c6d2b39876caaba09edd77761cb527cb170dac2d2a",
            ),
        )

        packages.forEach { runtimePackage ->
            val archive = File(temporaryDir, "${runtimePackage.packageName}-1.13.8.tar.gz")
            downloadVerifiedArchive(runtimePackage, archive)
            try {
                extractJniLibraries(archive, runtimePackage, output)
            } finally {
                archive.delete()
            }
        }

        val requiredLibraries = listOf(
            "libsherpa-onnx-c-api.so",
            "libsherpa-onnx-cxx-api.so",
            "libonnxruntime.so",
        )
        packages.forEach { runtimePackage ->
            val abiDirectory = File(output, runtimePackage.abi)
            requiredLibraries.forEach { library ->
                val file = File(abiDirectory, library)
                check(file.isFile && file.length() > 0L && isElfForAbi(file, runtimePackage.abi)) {
                    "The pinned sherpa-onnx package is missing a valid ${runtimePackage.abi}/$library"
                }
            }
            // The Termux-built C API uses the NDK shared C++ runtime. The upstream package
            // normally includes it; copy an NDK-matched fallback when the package omits it.
            val cxxRuntime = File(abiDirectory, "libc++_shared.so")
            if (!cxxRuntime.exists()) {
                findNdkCxxRuntime(runtimePackage.abi)?.copyTo(cxxRuntime, overwrite = true)
            }
            check(cxxRuntime.isFile && cxxRuntime.length() > 0L && isElfForAbi(cxxRuntime, runtimePackage.abi)) {
                "No libc++_shared.so is available for ${runtimePackage.abi}; install the Android NDK or use a complete sherpa package"
            }
        }
    }

    private fun downloadVerifiedArchive(runtimePackage: RuntimePackage, output: File) {
        val url = "https://pub.dev/api/archives/${runtimePackage.packageName}-1.13.8.tar.gz"
        val partial = File(temporaryDir, output.name + ".part")
        partial.delete()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("User-Agent", "RP-Ideas-VocalRemover-build/0.1")
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Could not download pinned ${runtimePackage.packageName} runtime (HTTP ${connection.responseCode})"
            }
            val finalUrl = connection.url
            check(finalUrl.protocol == "https" && finalUrl.host.lowercase(Locale.ROOT) in ALLOWED_PACKAGE_HOSTS) {
                "The sherpa-onnx package download redirected to an untrusted host"
            }
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { target -> input.copyTo(target) }
            }
            check(sha256(partial) == runtimePackage.archiveSha256) {
                "Pinned ${runtimePackage.packageName} archive SHA-256 mismatch"
            }
            check(partial.renameTo(output)) { "Could not finalize the sherpa-onnx runtime archive" }
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    /** Extract only the ABI-specific JNI shared libraries from the checksum-verified tar.gz. */
    private fun extractJniLibraries(archive: File, runtimePackage: RuntimePackage, destination: File) {
        val marker = "android/src/main/jniLibs/${runtimePackage.abi}/"
        GZIPInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            val header = ByteArray(TAR_BLOCK_BYTES)
            while (readTarBlock(input, header)) {
                if (header.all { it == 0.toByte() }) break
                val name = tarField(header, 0, 100)
                val prefix = tarField(header, 345, 155)
                val archivePath = listOf(prefix, name).filter { it.isNotBlank() }.joinToString("/")
                    .replace('\\', '/')
                    .trimStart('/')
                val size = parseTarOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                require(size in 0L..MAX_TAR_ENTRY_BYTES) { "Unexpected native runtime archive entry size" }

                val markerIndex = archivePath.indexOf(marker)
                val relativePath = if (markerIndex >= 0) archivePath.substring(markerIndex + marker.length) else ""
                val libraryName = relativePath.takeIf {
                    (type == '\u0000' || type == '0') &&
                        it.isNotBlank() && !it.contains('/') &&
                        it.matches(Regex("lib[A-Za-z0-9_.+-]+\\.so"))
                }

                if (libraryName != null) {
                    val target = File(File(destination, runtimePackage.abi), libraryName)
                    target.parentFile?.mkdirs()
                    copyExactly(input, target, size)
                } else {
                    skipExactly(input, size)
                }
                val padding = (TAR_BLOCK_BYTES - (size % TAR_BLOCK_BYTES)).toInt() % TAR_BLOCK_BYTES
                skipExactly(input, padding.toLong())
            }
        }
    }

    private fun readTarBlock(input: GZIPInputStream, block: ByteArray): Boolean {
        var offset = 0
        while (offset < block.size) {
            val count = input.read(block, offset, block.size - offset)
            if (count < 0) {
                if (offset == 0) return false
                throw IOException("Truncated sherpa-onnx package archive")
            }
            offset += count
        }
        return true
    }

    private fun tarField(header: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { header[it] == 0.toByte() } ?: offset + length
        return String(header, offset, end - offset, Charsets.US_ASCII).trim()
    }

    private fun parseTarOctal(header: ByteArray, offset: Int, length: Int): Long {
        val field = String(header, offset, length, Charsets.US_ASCII)
            .trim('\u0000', ' ', '\t', '\n')
        if (field.isEmpty()) return 0L
        check(field.all { it in '0'..'7' }) { "Unsupported tar entry size encoding" }
        return field.toLong(8)
    }

    private fun copyExactly(input: GZIPInputStream, destination: File, size: Long) {
        FileOutputStream(destination).buffered().use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var remaining = size
            while (remaining > 0L) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) throw IOException("Truncated native runtime library")
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
    }

    private fun skipExactly(input: GZIPInputStream, byteCount: Long) {
        var remaining = byteCount
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (remaining > 0L) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("Truncated sherpa-onnx package archive")
            remaining -= count
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun isElfForAbi(file: File, abi: String): Boolean = runCatching {
        val header = ByteArray(20)
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                if (count < 0) return@runCatching false
                offset += count
            }
        }
        val is64Bit = header[4].toInt() == 2
        val isLittleEndian = header[5].toInt() == 1
        val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
        header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() && isLittleEndian &&
            when (abi) {
                "arm64-v8a" -> is64Bit && machine == 183 // EM_AARCH64
                "armeabi-v7a" -> !is64Bit && machine == 40 // EM_ARM
                else -> false
            }
    }.getOrDefault(false)

    private fun findNdkCxxRuntime(abi: String): File? {
        val triple = when (abi) {
            "arm64-v8a" -> "aarch64-linux-android"
            "armeabi-v7a" -> "arm-linux-androideabi"
            else -> return null
        }
        val candidates = linkedSetOf<File>()
        listOf("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT", "ANDROID_HOME", "ANDROID_SDK_ROOT").forEach { key ->
            System.getenv(key)?.takeIf { it.isNotBlank() }?.let(::File)?.let(candidates::add)
        }
        val localProperties = File(project.rootDir, "local.properties")
        if (localProperties.isFile) {
            val sdkPath = runCatching {
                localProperties.readLines().firstOrNull { it.trim().startsWith("sdk.dir=") }
                    ?.substringAfter('=')?.replace("\\:", ":")?.replace("\\\\", "\\")
            }.getOrNull()?.takeIf { it.isNotBlank() }
            sdkPath?.let(::File)?.let(candidates::add)
        }

        val ndkRoots = linkedSetOf<File>()
        candidates.forEach { candidate ->
            if (File(candidate, "toolchains/llvm/prebuilt").isDirectory) ndkRoots.add(candidate)
            File(candidate, "ndk-bundle").takeIf { it.isDirectory }?.let(ndkRoots::add)
            File(candidate, "ndk").listFiles()?.filter { it.isDirectory }?.forEach { ndkRoots.add(it) }
        }
        return ndkRoots.asSequence()
            .flatMap { ndk ->
                File(ndk, "toolchains/llvm/prebuilt").listFiles()?.asSequence() ?: emptySequence()
            }
            .map { host -> File(host, "sysroot/usr/lib/$triple/libc++_shared.so") }
            .firstOrNull { it.isFile && it.length() > 0L }
    }

    companion object {
        private const val TAR_BLOCK_BYTES = 512
        private const val COPY_BUFFER_BYTES = 256 * 1024
        private const val MAX_TAR_ENTRY_BYTES = 512L * 1024L * 1024L
        private val ALLOWED_PACKAGE_HOSTS = setOf("pub.dev", "storage.googleapis.com")
    }
}

val sherpaRuntime = tasks.register<DownloadSherpaRuntime>("downloadSherpaRuntime") {
    runtimeDirectory.set(layout.buildDirectory.dir("generated/sherpa-jni"))
}

android {
    sourceSets.getByName("main").java.srcDir(rootProject.file("shared/src/main/java"))
    namespace = "com.universalrp.vocalremover"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.universalrp.vocalremover"
        // API 29 is the current supported floor: scoped MediaStore output and Android thermal
        // status are used directly. Lower Android versions are not claimed as verified.
        minSdk = 29
        targetSdk = 36
        versionCode = 12
        versionName = "0.1.11"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/sherpa-jni"))

    signingConfigs {
        val stableKeystore = rootProject.file("ci-keystore/release.p12")
        if (stableKeystore.exists()) {
            create("stable") {
                storeFile = stableKeystore
                storePassword = "cleansweep-ci"
                keyAlias = "cleansweep"
                keyPassword = "cleansweep-ci"
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val stableKeystore = rootProject.file("ci-keystore/release.p12")
            if (stableKeystore.exists()) signingConfig = signingConfigs.getByName("stable")
        }
        debug {
            val stableKeystore = rootProject.file("ci-keystore/release.p12")
            if (stableKeystore.exists()) signingConfig = signingConfigs.getByName("stable")
        }
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
}

dependencies {
    // JNA calls the pinned sherpa-onnx source-separation C API packaged for Android.
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Native code is fetched and validated before Android variant tasks package JNI libraries.
tasks.configureEach {
    if (name.startsWith("preBuild") || name.contains("JniLibFolders") || name.startsWith("merge")) {
        dependsOn(sherpaRuntime)
    }
}
