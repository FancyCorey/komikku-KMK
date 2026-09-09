package eu.kanade.tachiyomi.data.sync.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.api.client.auth.oauth2.TokenResponseException
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class GoogleDriveSyncService(context: Context, json: Json, syncPreferences: SyncPreferences) : SyncService(
    context,
    json,
    syncPreferences,
) {
    constructor(context: Context) : this(
        context,
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        },
        Injekt.get<SyncPreferences>(),
    )

    enum class DeleteSyncDataStatus {
        NOT_INITIALIZED,
        NO_FILES,
        SUCCESS,
        ERROR,
    }

    private val appName = context.stringResource(MR.strings.app_name)

    private val remoteFileName = "${appName}_sync.proto.gz"

    private val googleDriveService = GoogleDriveService(context)

    private val protoBuf: ProtoBuf = Injekt.get()

    override suspend fun doSync(syncData: SyncData): Backup? {
        if (!beforeSync()) return null

        try {
            val remoteSData = pullSyncData()

            if (remoteSData != null) {
                // Get local unique device ID
                val localDeviceId = syncPreferences.uniqueDeviceID()
                val lastSyncDeviceId = remoteSData.deviceId

                logcat(LogPriority.DEBUG, "SyncService") {
                    "Compared local and remote sync device state"
                }

                // check if the last sync was done by the same device if so overwrite the remote data with the local data
                return if (lastSyncDeviceId == localDeviceId) {
                    pushSyncData(syncData)
                    syncData.backup
                } else {
                    // Merge the local and remote sync data
                    val mergedSyncData = mergeSyncData(syncData, remoteSData)
                    pushSyncData(mergedSyncData)
                    mergedSyncData.backup
                }
            }

            pushSyncData(syncData)
            return syncData.backup
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, "SyncService") { "Google Drive sync failed" }
            return null
        }
    }

    private suspend fun beforeSync(): Boolean {
        return googleDriveService.refreshToken()
    }

    private fun pullSyncData(): SyncData? {
        val drive = googleDriveService.driveService
            ?: throw Exception(context.stringResource(SYMR.strings.google_drive_not_signed_in))

        val fileList = getAppDataFileList(drive)
        if (fileList.isEmpty()) {
            logcat(LogPriority.INFO) { "No files found in app data" }
            return null
        }

        val gdriveFileId = fileList[0].id
        logcat(LogPriority.DEBUG) { "Google Drive sync file located" }

        try {
            drive.files().get(gdriveFileId).executeMediaAsInputStream().use { inputStream ->
                GZIPInputStream(inputStream).use { gzipInputStream ->
                    val byteArray = gzipInputStream.readBytes()
                    val backup = protoBuf.decodeFromByteArray(Backup.serializer(), byteArray)
                    val deviceId = fileList[0].appProperties["deviceId"] ?: ""
                    return SyncData(deviceId = deviceId, backup = backup)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Google Drive sync download failed" }
            throw Exception("Failed to download Google Drive sync data")
        }
    }

    private suspend fun pushSyncData(syncData: SyncData) {
        val drive = googleDriveService.driveService
            ?: throw Exception(context.stringResource(SYMR.strings.google_drive_not_signed_in))

        val fileList = getAppDataFileList(drive)
        val backup = syncData.backup ?: return

        val byteArray = protoBuf.encodeToByteArray(Backup.serializer(), backup)
        if (byteArray.isEmpty()) {
            throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
        }

        PipedOutputStream().use { pos ->
            PipedInputStream(pos).use { pis ->
                withIOContext {
                    launch {
                        GZIPOutputStream(pos).use { gzipOutputStream ->
                            gzipOutputStream.write(byteArray)
                        }
                    }

                    val mediaContent = InputStreamContent("application/octet-stream", pis)

                    if (fileList.isNotEmpty()) {
                        val fileId = fileList[0].id
                        val fileMetadata = File().apply {
                            name = remoteFileName
                            mimeType = "application/octet-stream"
                            appProperties = mapOf("deviceId" to syncData.deviceId)
                        }
                        drive.files().update(fileId, fileMetadata, mediaContent).execute()
                        logcat(LogPriority.DEBUG) {
                            "Updated existing Google Drive sync file"
                        }
                    } else {
                        val fileMetadata = File().apply {
                            name = remoteFileName
                            mimeType = "application/octet-stream"
                            parents = listOf("appDataFolder")
                            appProperties = mapOf("deviceId" to syncData.deviceId)
                        }
                        drive.files().create(fileMetadata, mediaContent)
                            .setFields("id")
                            .execute()
                        logcat(LogPriority.DEBUG) {
                            "Created new Google Drive sync file"
                        }
                    }
                }
            }
        }
    }

    private fun getAppDataFileList(drive: Drive): MutableList<File> {
        try {
            // Search for the existing file by name in the appData folder
            val query = "mimeType='application/x-gzip' and name = '$remoteFileName'"
            val fileList = drive.files()
                .list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .setFields("files(id, name, createdTime, appProperties)")
                .execute()
                .files
            logcat { "Google Drive app-data sync file count=${fileList.size}" }

            return fileList
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Google Drive app-data lookup failed" }
            return mutableListOf()
        }
    }

    suspend fun deleteSyncDataFromGoogleDrive(): DeleteSyncDataStatus {
        val drive = googleDriveService.driveService

        if (drive == null) {
            logcat(LogPriority.ERROR) { "Google Drive service not initialized" }
            return DeleteSyncDataStatus.NOT_INITIALIZED
        }
        if (!googleDriveService.refreshToken()) return DeleteSyncDataStatus.NOT_INITIALIZED

        return withIOContext {
            try {
                val appDataFileList = getAppDataFileList(drive)

                if (appDataFileList.isEmpty()) {
                    this@GoogleDriveSyncService
                        .logcat(LogPriority.DEBUG) { "No sync data file found in appData folder of Google Drive" }
                    DeleteSyncDataStatus.NO_FILES
                } else {
                    for (file in appDataFileList) {
                        drive.files().delete(file.id).execute()
                        this@GoogleDriveSyncService.logcat(
                            LogPriority.DEBUG,
                        ) { "Deleted Google Drive sync file from app-data storage" }
                    }
                    DeleteSyncDataStatus.SUCCESS
                }
            } catch (e: Exception) {
                this@GoogleDriveSyncService.logcat(LogPriority.ERROR) {
                    "Google Drive sync-file deletion failed"
                }
                DeleteSyncDataStatus.ERROR
            }
        }
    }
}

class GoogleDriveService(private val context: Context) {
    var driveService: Drive? = null
    companion object {
        const val REDIRECT_URI =
            "${GoogleDriveAuthorizationCallbackPolicy.REDIRECT_SCHEME}:${GoogleDriveAuthorizationCallbackPolicy.REDIRECT_PATH}"
    }
    private val syncPreferences = Injekt.get<SyncPreferences>()

    private data class ClientSecretsLoadResult(
        val state: GoogleDriveClientConfigurationState,
        val secrets: GoogleClientSecrets? = null,
    )

    private fun loadClientSecrets(): ClientSecretsLoadResult {
        val assetName = BuildConfig.GOOGLE_DRIVE_CLIENT_SECRETS_ASSET
        if (assetName.isBlank()) {
            return ClientSecretsLoadResult(GoogleDriveClientConfigurationState.MISSING_ASSET)
        }

        return runCatching {
            val jsonFactory = GsonFactory.getDefaultInstance()
            val secrets = context.assets.open(assetName).reader().use { reader ->
                GoogleClientSecrets.load(jsonFactory, reader)
            }
            val clientId = secrets.installed?.clientId ?: secrets.web?.clientId
            val state = GoogleDriveClientConfigurationPolicy.classify(
                clientSecretsAvailable = true,
                parsedClientId = clientId,
                expectedClientId = BuildConfig.GOOGLE_DRIVE_CLIENT_ID,
            )
            ClientSecretsLoadResult(state, secrets.takeIf { state == GoogleDriveClientConfigurationState.AVAILABLE })
        }.getOrElse {
            ClientSecretsLoadResult(GoogleDriveClientConfigurationState.MALFORMED_ASSET)
        }
    }

    private fun availableClientSecrets(): GoogleClientSecrets? = loadClientSecrets().secrets

    private fun logUnavailableConfiguration(state: GoogleDriveClientConfigurationState) {
        this.logcat(LogPriority.WARN) {
            "Google Drive unavailable: client configuration state=${state.name}"
        }
    }

    init {
        initGoogleDriveService()
    }

    /**
     * Initializes the Google Drive service by obtaining the access token and refresh token from the SyncPreferences
     * and setting up the service using the obtained tokens.
     */
    private fun initGoogleDriveService() {
        val accessToken = syncPreferences.googleDriveAccessToken().get()
        val refreshToken = syncPreferences.googleDriveRefreshToken().get()

        val configurationState = loadClientSecrets().state
        val tokenState = GoogleDriveTokenLifecyclePolicy.initialState(
            configurationAvailable = !GoogleDriveAvailabilityPolicy.shouldSkipBackgroundSync(configurationState),
            refreshTokenPresent = refreshToken.isNotBlank(),
        )
        if (accessToken.isBlank() ||
            tokenState != GoogleDriveTokenLifecycleState.READY
        ) {
            driveService = null
            return
        }

        setupGoogleDriveService(accessToken, refreshToken)
    }

    /**
     * Launches an Intent to open the user's default browser for Google Drive sign-in.
     * The Intent carries the authorization URL, which prompts the user to sign in
     * and grant the application permission to access their Google Drive account.
     * @return An Intent configured to launch a browser for Google Drive OAuth sign-in.
     */
    fun getSignInIntent(): Intent? {
        val authorizationUrl = generateAuthorizationUrl() ?: return null

        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(authorizationUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Generates the authorization URL required for the user to grant the application
     * permission to access their Google Drive account.
     * Sets the approval prompt to "force" to ensure that the user is always prompted to grant access,
     * even if they have previously granted access.
     * @return The authorization URL.
     */
    private fun generateAuthorizationUrl(): String? {
        val jsonFactory: GsonFactory = GsonFactory.getDefaultInstance()
        val secrets = availableClientSecrets() ?: run {
            logUnavailableConfiguration(loadClientSecrets().state)
            return null
        }

        val flow = GoogleAuthorizationCodeFlow.Builder(
            NetHttpTransport(),
            jsonFactory,
            secrets,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA),
        ).setAccessType("offline").build()

        return flow.newAuthorizationUrl()
            .setRedirectUri(REDIRECT_URI)
            .setApprovalPrompt("force")
            .build()
    }
    internal suspend fun refreshToken(): Boolean = withIOContext {
        val clientSecretsResult = loadClientSecrets()
        if (GoogleDriveAvailabilityPolicy.shouldSkipBackgroundSync(clientSecretsResult.state)) {
            logUnavailableConfiguration(clientSecretsResult.state)
            return@withIOContext false
        }
        val refreshToken = syncPreferences.googleDriveRefreshToken().get()
        if (GoogleDriveTokenLifecyclePolicy.initialState(
                configurationAvailable = true,
                refreshTokenPresent = refreshToken.isNotBlank(),
            ) == GoogleDriveTokenLifecycleState.MISSING_REFRESH_TOKEN
        ) {
            driveService = null
            this@GoogleDriveService.logcat(LogPriority.INFO) {
                "Google Drive refresh skipped because authorization is not configured"
            }
            return@withIOContext false
        }

        val jsonFactory: GsonFactory = GsonFactory.getDefaultInstance()
        val secrets = clientSecretsResult.secrets ?: return@withIOContext false

        val credential = GoogleCredential.Builder()
            .setJsonFactory(jsonFactory)
            .setTransport(NetHttpTransport())
            .setClientSecrets(secrets)
            .build()

        val tokenStore = object : GoogleDriveTokenStore {
            override fun refreshToken(): String = syncPreferences.googleDriveRefreshToken().get()

            override fun saveAccessToken(accessToken: String) {
                syncPreferences.googleDriveAccessToken().set(accessToken)
            }

            override fun clearCredentials() {
                syncPreferences.googleDriveAccessToken().set("")
                syncPreferences.googleDriveRefreshToken().set("")
            }
        }
        val state = GoogleDriveTokenRefreshCoordinator.refresh(
            configurationAvailable = true,
            tokenStore = tokenStore,
            tokenRefresher = GoogleCredentialTokenRefresher(credential),
        )

        when (state) {
            GoogleDriveTokenLifecycleState.READY -> {
                val accessToken = syncPreferences.googleDriveAccessToken().get()
                setupGoogleDriveService(accessToken, refreshToken)
                driveService != null
            }

            GoogleDriveTokenLifecycleState.AUTHORIZATION_REVOKED -> {
                driveService = null
                this@GoogleDriveService.logcat(LogPriority.ERROR) {
                    "Google Drive refresh token is invalid"
                }
                false
            }

            GoogleDriveTokenLifecycleState.MALFORMED_RESPONSE -> {
                driveService = null
                this@GoogleDriveService.logcat(LogPriority.ERROR) {
                    "Google Drive token response was unusable"
                }
                false
            }

            GoogleDriveTokenLifecycleState.TRANSIENT_FAILURE -> {
                driveService = null
                this@GoogleDriveService.logcat(LogPriority.ERROR) {
                    "Google Drive access-token refresh failed"
                }
                false
            }

            GoogleDriveTokenLifecycleState.CANCELED -> {
                driveService = null
                false
            }

            GoogleDriveTokenLifecycleState.MISSING_CONFIGURATION,
            GoogleDriveTokenLifecycleState.MISSING_REFRESH_TOKEN,
            -> {
                driveService = null
                false
            }
        }
    }

    /**
     * Sets up the Google Drive service using the provided access token and refresh token.
     * @param accessToken The access token obtained from the SyncPreferences.
     * @param refreshToken The refresh token obtained from the SyncPreferences.
     */
    private fun setupGoogleDriveService(accessToken: String, refreshToken: String) {
        val jsonFactory: GsonFactory = GsonFactory.getDefaultInstance()
        val secrets = availableClientSecrets() ?: run {
            logUnavailableConfiguration(loadClientSecrets().state)
            driveService = null
            return
        }

        val credential = GoogleCredential.Builder()
            .setJsonFactory(jsonFactory)
            .setTransport(NetHttpTransport())
            .setClientSecrets(secrets)
            .build()

        credential.accessToken = accessToken
        credential.refreshToken = refreshToken

        driveService = Drive.Builder(
            NetHttpTransport(),
            jsonFactory,
            credential,
        ).setApplicationName(context.stringResource(MR.strings.app_name))
            .build()
    }

    /**
     * Handles the authorization code returned after the user has granted the application permission to access their
     * Google Drive account.
     * It obtains the access token and refresh token using the authorization code, saves the tokens to the
     * SyncPreferences, sets up the Google Drive service using the obtained tokens, and initializes the service.
     * @param authorizationCode The authorization code obtained from the OAuthCallbackServer.
     * @param activity The current activity.
     * @param onSuccess A callback function to be called on successful authorization.
     * @param onFailure A callback function to be called on authorization failure.
     */
    fun handleAuthorizationCode(
        authorizationCode: String,
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val jsonFactory: GsonFactory = GsonFactory.getDefaultInstance()
        val secrets = availableClientSecrets() ?: run {
            logUnavailableConfiguration(loadClientSecrets().state)
            activity.runOnUiThread {
                onFailure(context.stringResource(SYMR.strings.google_drive_not_signed_in))
            }
            return
        }

        try {
            val tokenResponse: GoogleTokenResponse = GoogleAuthorizationCodeTokenRequest(
                NetHttpTransport(),
                jsonFactory,
                secrets.installed.clientId,
                secrets.installed.clientSecret,
                authorizationCode,
                REDIRECT_URI,
            ).setGrantType("authorization_code").execute()

            // Save the access token and refresh token
            val accessToken = tokenResponse.accessToken
            val refreshToken = tokenResponse.refreshToken

            // Save the tokens to SyncPreferences
            syncPreferences.googleDriveAccessToken().set(accessToken)
            syncPreferences.googleDriveRefreshToken().set(refreshToken)

            setupGoogleDriveService(accessToken, refreshToken)
            initGoogleDriveService()

            activity.runOnUiThread {
                onSuccess()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Google Drive authorization handling failed" }
            activity.runOnUiThread {
                onFailure(context.stringResource(SYMR.strings.google_drive_not_signed_in))
            }
        }
    }
}

private class GoogleCredentialTokenRefresher(
    private val credential: GoogleCredential,
) : GoogleDriveTokenRefresher {
    override fun refresh(refreshToken: String): GoogleDriveRefreshResult {
        credential.refreshToken = refreshToken
        return try {
            credential.refreshToken()
            val accessToken = credential.accessToken
            if (accessToken.isNullOrBlank()) {
                GoogleDriveRefreshResult.MalformedResponse
            } else {
                GoogleDriveRefreshResult.Success(accessToken)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TokenResponseException) {
            if (e.details?.error == "invalid_grant") {
                GoogleDriveRefreshResult.AuthorizationRevoked
            } else {
                GoogleDriveRefreshResult.TransientFailure
            }
        } catch (e: IOException) {
            throw e
        }
    }
}
