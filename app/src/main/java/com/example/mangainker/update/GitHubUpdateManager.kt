package com.example.mangainker.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.example.mangainker.core.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

data class UpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val message: String = "Private GitHub update channel. Add a read-only token once.",
    val tokenConfigured: Boolean = false,
    val updateAvailable: Boolean = false,
    val downloadedApk: File? = null,
    val currentVersionName: String = "",
    val latestVersionName: String = "",
    val latestVersionCode: Long = 0L,
    val releaseNotes: String = "",
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val lastCheckedAtMs: Long? = null,
    val checkOnLaunch: Boolean = true
)

class GitHubUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tokenStore = GitHubTokenStore(appContext)
    private var automaticCheckJob: Job? = null
    private val preferences = appContext.getSharedPreferences("manga_inker_update", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(
        UpdateUiState(
            tokenConfigured = tokenStore.load() != null,
            currentVersionName = currentVersionName(),
            checkOnLaunch = preferences.getBoolean(KEY_CHECK_ON_LAUNCH, true)
        )
    )
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()

    fun startAutomaticChecks() {
        if (!mutableState.value.checkOnLaunch || automaticCheckJob?.isActive == true) return
        automaticCheckJob = scope.launch {
            checkForUpdate()
            while (isActive) {
                delay(AUTO_CHECK_INTERVAL_MS)
                checkForUpdate()
            }
        }
    }

    fun setCheckOnLaunch(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CHECK_ON_LAUNCH, enabled).apply()
        mutableState.value = mutableState.value.copy(checkOnLaunch = enabled)
        if (enabled) {
            startAutomaticChecks()
        } else {
            automaticCheckJob?.cancel()
            automaticCheckJob = null
        }
    }

    fun saveTokenAndCheck(token: String) {
        val clean = token.trim()
        if (clean.length < 20 || clean.any { it.isWhitespace() }) {
            mutableState.value = mutableState.value.copy(
                message = "That token does not look valid. Use a fine-grained GitHub token with Contents: Read for this private repository."
            )
            return
        }
        try {
            tokenStore.save(clean)
            mutableState.value = mutableState.value.copy(
                tokenConfigured = true,
                message = "Token encrypted on this device. Checking GitHub…"
            )
            checkForUpdate()
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(
                message = "Could not securely save the token: " + (error.message ?: "unknown error")
            )
        }
    }

    fun clearToken() {
        tokenStore.clear()
        mutableState.value = UpdateUiState(
            currentVersionName = currentVersionName(),
            checkOnLaunch = preferences.getBoolean(KEY_CHECK_ON_LAUNCH, true),
            message = "GitHub update access removed from this device."
        )
        Logger.info("GitHub update credential removed")
    }

    fun checkForUpdate() {
        val token = tokenStore.load()
        if (token.isNullOrBlank()) {
            mutableState.value = mutableState.value.copy(
                tokenConfigured = false,
                currentVersionName = currentVersionName(),
                lastCheckedAtMs = System.currentTimeMillis(),
                message = "Add a fine-grained GitHub token once to check updates for this private repository."
            )
            return
        }
        if (mutableState.value.checking || mutableState.value.downloading) return

        scope.launch {
            mutableState.value = mutableState.value.copy(
                checking = true,
                tokenConfigured = true,
                message = "Checking the latest Manga Inker build on GitHub…"
            )
            try {
                val release = getJson(
                    RELEASE_API,
                    token,
                    accept = "application/vnd.github+json"
                )
                val releaseNotes = release.optString("body", "")
                val assets = release.getJSONArray("assets")
                val manifestAsset = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == "update.json" }
                    ?: error("The release is missing update.json. The latest build may still be publishing.")
                val apkAsset = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == "app-release.apk" }
                    ?: error("The release is missing app-release.apk.")

                val manifestText = getText(
                    manifestAsset.getString("url"),
                    token,
                    accept = "application/octet-stream"
                )
                val manifest = JSONObject(manifestText)
                require(manifest.optString("packageName") == appContext.packageName) {
                    "The GitHub release belongs to a different Android package."
                }
                val latestCode = manifest.getLong("versionCode")
                val latestName = manifest.getString("versionName")
                val currentCode = currentVersionCode()
                if (latestCode <= currentCode) {
                    mutableState.value = UpdateUiState(
                        tokenConfigured = true,
                        currentVersionName = currentVersionName(),
                        lastCheckedAtMs = System.currentTimeMillis(),
                        checkOnLaunch = preferences.getBoolean(KEY_CHECK_ON_LAUNCH, true),
                        releaseNotes = releaseNotes,
                        message = "You're up to date · " + currentVersionName()
                    )
                    return@launch
                }

                mutableState.value = mutableState.value.copy(
                    downloading = true,
                    checking = false,
                    currentVersionName = currentVersionName(),
                    latestVersionCode = latestCode,
                    latestVersionName = latestName,
                    releaseNotes = releaseNotes,
                    downloadProgress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = apkAsset.optLong("size", 0L),
                    message = "New version " + latestName + " found. Downloading it from GitHub…"
                )

                val apk = File(appContext.cacheDir, "manga-inker-update-$latestCode.apk")
                val assetUrl = apkAsset.getString("url")
                val expectedSize = apkAsset.optLong("size", 0L)
                downloadAsset(assetUrl, token, apk, expectedSize)
                val expected = manifest.getString("sha256").lowercase()
                val actual = sha256(apk).lowercase()
                require(actual == expected) {
                    apk.delete()
                    "The downloaded APK checksum did not match GitHub's update manifest. The file was rejected."
                }

                mutableState.value = UpdateUiState(
                    tokenConfigured = true,
                    updateAvailable = true,
                    downloadedApk = apk,
                    currentVersionName = currentVersionName(),
                    latestVersionName = latestName,
                    latestVersionCode = latestCode,
                    releaseNotes = releaseNotes,
                    downloadProgress = 1f,
                    downloadedBytes = apk.length(),
                    totalBytes = apk.length(),
                    lastCheckedAtMs = System.currentTimeMillis(),
                    checkOnLaunch = preferences.getBoolean(KEY_CHECK_ON_LAUNCH, true),
                    message = "Version " + latestName + " is downloaded and verified. Tap Install update."
                )
                Logger.info("Update downloaded and SHA-256 verified version=" + latestName)
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    checking = false,
                    downloading = false,
                    lastCheckedAtMs = System.currentTimeMillis(),
                    message = "Update check failed: " + (error.message ?: error.javaClass.simpleName)
                )
                Logger.error("GitHub update failed: " + (error.message ?: error.javaClass.simpleName))
            }
        }
    }

    fun installUpdate() {
        val apk = mutableState.value.downloadedApk
        if (apk == null || !apk.isFile) {
            mutableState.value = mutableState.value.copy(message = "No verified update is ready. Check GitHub again.")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            mutableState.value = mutableState.value.copy(
                message = "Allow Manga Inker to install updates in Android settings, then tap Install update again."
            )
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + appContext.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            return
        }

        scope.launch {
            try {
                val installer = appContext.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(appContext.packageName)
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("base.apk", 0, apk.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    val callback = Intent(appContext, UpdateInstallReceiver::class.java)
                    val pending = PendingIntent.getBroadcast(
                        appContext,
                        sessionId,
                        callback,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    session.commit(pending.intentSender)
                }
                mutableState.value = mutableState.value.copy(
                    message = "Verified update sent to Android. Confirm the system installation prompt to finish."
                )
                Logger.info("Android package installer opened for update")
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    message = "Could not open Android's installer: " + (error.message ?: error.javaClass.simpleName)
                )
                Logger.error("Package install failed: " + (error.message ?: error.javaClass.simpleName))
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun currentVersionCode(): Long {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun currentVersionName(): String =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "installed version"

    private fun getJson(url: String, token: String, accept: String): JSONObject =
        JSONObject(getText(url, token, accept))

    private fun getText(url: String, token: String, accept: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("Accept", accept)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Manga-Inker-Android")
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw IllegalStateException(
                    when (code) {
                        401, 403 -> "GitHub rejected the token. It needs Contents: Read access to this repository."
                        404 -> "GitHub could not find the private mobile-latest release. Check repository access and workflow status."
                        else -> "GitHub HTTP " + code + if (message.isNullOrBlank()) "" else ": " + message
                    }
                )
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAsset(url: String, token: String, target: File, expectedSize: Long) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Manga-Inker-Android")
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("GitHub APK download failed with HTTP " + code + ". " + detail.take(180))
            }
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: expectedSize
            var downloaded = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        mutableState.value = mutableState.value.copy(
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            downloadProgress = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                        )
                    }
                }
            }
            require(target.length() > 1_000_000L) {
                target.delete()
                "Downloaded update is too small to be a valid Manga Inker APK."
            }
            require(expectedSize <= 0L || target.length() == expectedSize) {
                target.delete()
                "Downloaded APK size does not match the GitHub release asset metadata."
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    companion object {
        private const val KEY_CHECK_ON_LAUNCH = "check_on_launch"
        private const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1_000L
        private const val RELEASE_API =
            "https://api.github.com/repos/rohit42i/-mangaink/releases/tags/mobile-latest"
    }
}

private class GitHubTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("manga_inker_update", Context.MODE_PRIVATE)
    private val alias = "manga_inker_github_update_token"

    fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString("token_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token_value", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? {
        val iv = preferences.getString("token_iv", null) ?: return null
        val value = preferences.getString("token_value", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (error: Exception) {
            clear()
            Logger.error("Could not decrypt saved GitHub update token: " + error.javaClass.simpleName)
            null
        }
    }

    fun clear() {
        preferences.edit().remove("token_iv").remove("token_value").apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
