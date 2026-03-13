package com.fcm.nanochat.data

import android.content.Context
import com.fcm.nanochat.data.db.AppDatabase
import com.fcm.nanochat.data.repository.ChatRepository
import com.fcm.nanochat.data.repository.LocalModelRepository
import com.fcm.nanochat.inference.DownloadedModelInferenceClient
import com.fcm.nanochat.inference.LocalInferenceClient
import com.fcm.nanochat.inference.RemoteInferenceClient
import com.fcm.nanochat.models.allowlist.AllowlistRepository
import com.fcm.nanochat.models.allowlist.BundledAllowlistSource
import com.fcm.nanochat.models.allowlist.CachedAllowlistSource
import com.fcm.nanochat.models.allowlist.RemoteAllowlistSource
import com.fcm.nanochat.models.compatibility.LocalModelCompatibilityEvaluator
import com.fcm.nanochat.models.download.DownloadIntegrityValidator
import com.fcm.nanochat.models.download.ModelDownloadCoordinator
import com.fcm.nanochat.models.importing.StubLocalModelImportCoordinator
import com.fcm.nanochat.models.registry.ModelRegistry
import com.fcm.nanochat.models.runtime.InMemoryLocalRuntimeTelemetry
import com.fcm.nanochat.models.runtime.ModelRuntimeManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.create(appContext)
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    val preferences = AppPreferences(appContext)

    private val allowlistRepository = AllowlistRepository(
        preferences = preferences,
        bundledSource = BundledAllowlistSource(
            context = appContext,
            assetName = BUNDLED_ALLOWLIST_ASSET,
            bundledVersion = BUNDLED_ALLOWLIST_VERSION
        ),
        cachedSource = CachedAllowlistSource(preferences),
        remoteSource = REMOTE_ALLOWLIST_URL.takeIf { it.isNotBlank() }?.let { remoteUrl ->
            RemoteAllowlistSource(
                httpClient = httpClient,
                url = remoteUrl,
                fallbackVersion = BUNDLED_ALLOWLIST_VERSION
            )
        }
    )

    private val runtimeTelemetry = InMemoryLocalRuntimeTelemetry()
    private val runtimeManager = ModelRuntimeManager(appContext)

    private val compatibilityEvaluator = LocalModelCompatibilityEvaluator(appContext)

    private val modelRegistry = ModelRegistry(
        allowlistRepository = allowlistRepository,
        installedModelDao = database.installedModelDao(),
        preferences = preferences,
        compatibilityEvaluator = compatibilityEvaluator
    )

    private val modelDownloadCoordinator = ModelDownloadCoordinator(
        context = appContext,
        httpClient = httpClient,
        preferences = preferences,
        allowlistRepository = allowlistRepository,
        installedModelDao = database.installedModelDao(),
        integrityValidator = DownloadIntegrityValidator()
    )

    private val importCoordinator = StubLocalModelImportCoordinator()

    val localModelRepository = LocalModelRepository(
        allowlistRepository = allowlistRepository,
        modelRegistry = modelRegistry,
        downloadCoordinator = modelDownloadCoordinator,
        importCoordinator = importCoordinator,
        telemetry = runtimeTelemetry,
        runtimeManager = runtimeManager
    )

    private val localInferenceClient = LocalInferenceClient()
    private val downloadedInferenceClient = DownloadedModelInferenceClient(
        modelRegistry = modelRegistry,
        runtimeManager = runtimeManager,
        telemetry = runtimeTelemetry
    )
    private val remoteInferenceClient = RemoteInferenceClient(httpClient)

    val chatRepository = ChatRepository(
        database = database,
        preferences = preferences,
        localModelRepository = localModelRepository,
        localInferenceClient = localInferenceClient,
        downloadedInferenceClient = downloadedInferenceClient,
        remoteInferenceClient = remoteInferenceClient
    )

    init {
        localModelRepository.reconcile()
    }

    companion object {
        private const val BUNDLED_ALLOWLIST_ASSET = "model_allowlist_1_0_10.json"
        private const val BUNDLED_ALLOWLIST_VERSION = "1_0_10"
        private const val REMOTE_ALLOWLIST_URL =
            "https://raw.githubusercontent.com/google-ai-edge/gallery/main/model_allowlists/1_0_10.json"
    }
}
