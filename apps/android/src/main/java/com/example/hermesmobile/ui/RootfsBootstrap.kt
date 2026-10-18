package com.example.hermesmobile.ui

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac

/**
 * AsyncTask that downloads a rootfs tarball, verifies its SHA-256 checksum,
 * extracts it into the app's private files directory, and signals completion.
 *
 * Expected rootfs: Debian ARM64 minimal tarball (e.g. debian-arm64-minimal.tar.xz)
 * Target location: context.filesDir / "linux/rootfs"
 *
 * Usage: RootfsBootstrap.execute(context, url, expectedSha256)
 */
object RootfsBootstrap {

    // Example Debian ARM64 minimal rootfs URL (replace with a verified mirror)
    // This is a placeholder URL; in production use a trusted mirror with a known good hash.
    private const val EXAMPLE_ROOTFS_URL =
        "https://example.com/minimal-rootfs/debian-arm64-minimal.tar.xz"

    // Expected SHA-256 of the above example URL (replace with real hash)
    private const val EXAMPLE_ROOTFS_SHA256 = "0000000000000000000000000000000000000000000000000000000000000000"

    @JvmStatic
    fun execute(
        context: Context,
        url: String = EXAMPLE_ROOTFS_URL,
        expectedSha256: String = EXAMPLE_ROOTFS_SHA256
    ) {
        // Check if rootfs already exists
        val rootfsDir = File(context.filesDir, "linux/rootfs")
        if (rootfsDir.exists() && rootfsDir.isDirectory) {
            Log.i("RootfsBootstrap", "Rootfs already present at ${rootfsDir.absolutePath}")
            // TODO: verify integrity if desired; if valid, mark bootstrapped
            return
        }

        // Start async download
        DownloadTask(context, rootfsDir, url, expectedSha256).execute()
    }

    /** Download + verify + extract task. */
    private class DownloadTask(
        private val ctx: Context,
        private val targetDir: File,
        private val url: String,
        private val expectedSha256: String
    ) : AsyncTask<Void, Int, Boolean>() {

        private var downloadUrl: String = url
        private var expectedHash: String = expectedSha256

        override fun doInBackground(*params: Void?): Boolean {
            try {
                // 1. Create target directory structure
                targetDir.mkdirs()
                val devDir = File(targetDir, "dev")
                val procDir = File(targetDir, "proc")
                val sysDir = File(targetDir, "sys")
                devDir.mkdirs()
                procDir.mkdirs()
                sysDir.mkdirs()

                // 2. Download tarball
                val tarballFile = File(targetDir, "rootfs.tar.xz")
                Log.i("RootfsBootstrap", "Downloading rootfs from $downloadUrl")
                if (!downloadFile(downloadUrl, tarballFile)) {
                    Log.e("RootfsBootstrap", "Failed to download rootfs tarball")
                    return false
                }

                // 3. Verify SHA-256
                val actualHash = sha256(tarballFile)
                if (actualHash != expectedHash) {
                    Log.e(
                        "RootfsBootstrap",
                        "SHA-256 mismatch: expected $expectedHash, got $actualHash"
                    )
                    // Clean up partial download
                    tarballFile.delete()
                    return false
                }
                Log.i("RootfsBootstrap", "Rootfs SHA-256 verified: $actualHash")

                // 4. Extract tarball
                if (!extractTarball(tarballFile, targetDir)) {
                    Log.e("RootfsBootstrap", "Failed to extract rootfs tarball")
                    return false
                }
                Log.i("RootfsBootstrap", "Rootfs extracted to ${targetDir.absolutePath}")

                // 5. Minimal configuration: ensure /etc/resolv.conf points to Google DNS
                // (optional: write a stub if not present)
                val resolv = File(targetDir, "etc/resolv.conf")
                if (!resolv.exists()) {
                    resolv.parentFile.mkdirs()
                    resolv.bufferedWriter().use { writer ->
                        writer.write("nameserver 8.8.8.8\n")
                        writer.write("nameserver 8.8.4.4\n")
                    }
                    Log.i("RootfsBootstrap", "Wrote stub /etc/resolv.conf")
                }

                // 6. Mark bootstrapped (e.g., write a flag file)
                val flagFile = File(targetDir, ".bootstrapped")
                flagFile.createNewFile()
                Log.i("RootfsBootstrap", "Rootfs bootstrapped successfully")

                return true
            } catch (e: Exception) {
                Log.e("RootfsBootstrap", "Rootfs bootstrap failed", e)
                return false
            }
        }

        override fun onPostExecute(success: Boolean) {
            if (success) {
                Log.i("RootfsBootstrap", "Rootfs is ready for proot usage")
                // TODO: signal MainActivity or controller so UI can enable terminal mode
            } else {
                Log.w("RootfsBootstrap", "Rootfs bootstrap failed — terminal will be disabled")
                // TODO: show user-friendly error; maybe retry button
            }
        }

        /** Download a file from URL to destination, returning true on success. */
        private fun downloadFile(urlString: String, dest: File): Boolean {
            try {
                val url = java.net.URL(urlString)
                val connection = url.openConnection()
                connection.connectTimeout = 30_000
                ReadTimeout = 30_000
                connection.setRequestProperty("User-Agent", "HermesMobile/1.0")
                connection.connect()

                val input = connection.inputStream
                val output = FileOutputStream(dest)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (isCancelled) {
                    return false
                }
                while ((bytesRead = input.read(buffer)) != -1) {
                    if (isCancelled) {
                        return false
                    }
                    output.write(buffer, 0, bytesRead)
                }
                output.close()
                input.close()
                return dest.length() > 0
            } catch (e: Exception) {
                Log.e("RootfsBootstrap", "Error downloading $url to ${dest.absolutePath}", e)
                return false
            }
        }

        /** Compute SHA-256 of a file. */
        private fun sha256(file: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val fis = java.io.FileInputStream(file)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            fis.close()
            val hashBytes = digest.digest()
            val hexString = StringBuilder()
            for (b in hashBytes) {
                hexString.append(String.format("%02x", b))
            }
            return hexString.toString()
        }

        /** Extract a .tar.xz tarball into the given directory. */
        private fun extractTarball(tarball: File, dest: File): Boolean {
            // Use busybox tar or Python's tarfile if available; fallback to Runtime.exec
            try {
                // Prefer busybox if available on the device's shell, but we are in Java here.
                // Use Python if available, otherwise Runtime.exec tar.
                // For simplicity in this scaffold, we Runtime.exec 'tar xJf ... -C ...'
                // Note: on a device without tar, this will fail — production would bundle a minimal tar.
                val cmd = "tar xJf ${tarball.absolutePath} -C ${dest.absolutePath}"
                val process = Runtime.getRuntime().exec(cmd)
                val exitCode = process.waitFor()
                return exitCode == 0
            } catch (e: Exception) {
                Log.e("RootfsBootstrap", "Error extracting tarball via Runtime.exec", e)
                // Fallback: attempt Python extraction if Python is on PATH
                try {
                    val pythonCmd = "python3 -c \"import tarfile, sys; tarfile.open('${tarball.absolutePath}', 'r:xz').extractall('${dest.absolutePath}')\""
                    val process = Runtime.getRuntime().exec(pythonCmd)
                    val exitCode = process.waitFor()
                    return exitCode == 0
                } catch (e2: Exception) {
                    Log.e("RootfsBootstrap", "Python fallback also failed", e2)
                    return false
                }
            }
        }
    }
}