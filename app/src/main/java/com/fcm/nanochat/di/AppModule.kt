package com.fcm.nanochat.di

import android.content.Context
import com.fcm.nanochat.data.AppPreferences
import com.fcm.nanochat.data.db.AppDatabase
import com.fcm.nanochat.data.db.InstalledModelDao
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
import com.fcm.nanochat.models.importing.LocalModelImportCoordinator
import com.fcm.nanochat.models.importing.StubLocalModelImportCoordinator
import com.fcm.nanochat.models.registry.ModelRegistry
import com.fcm.nanochat.models.runtime.InMemoryLocalRuntimeTelemetry
import com.fcm.nanochat.models.runtime.ModelRuntimeManager
import com.fcm.nanochat.notifications.NotificationCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences {
        return AppPreferences(context)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.create(context)
    }

    @Provides
    fun provideInstalledModelDao(database: AppDatabase): InstalledModelDao {
        return database.installedModelDao()
    }

    @Provides
    @Singleton
    fun provideAllowlistRepository(
        @ApplicationContext context: Context,
        preferences: AppPreferences,
        httpClient: OkHttpClient
    ): AllowlistRepository {
        return AllowlistRepository(
            preferences = preferences,
            bundledSource = BundledAllowlistSource(
                context = context,
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
    }

    @Provides
    @Singleton
    fun provideRuntimeTelemetry(): InMemoryLocalRuntimeTelemetry {
        return InMemoryLocalRuntimeTelemetry()
    }

    @Provides
    @Singleton
    fun provideModelRuntimeManager(@ApplicationContext context: Context): ModelRuntimeManager {
        return ModelRuntimeManager(context)
    }

    @Provides
    @Singleton
    fun provideCompatibilityEvaluator(
        @ApplicationContext context: Context
    ): LocalModelCompatibilityEvaluator {
        return LocalModelCompatibilityEvaluator(context)
    }

    @Provides
    @Singleton
    fun provideModelRegistry(
        allowlistRepository: AllowlistRepository,
        installedModelDao: InstalledModelDao,
        preferences: AppPreferences,
        compatibilityEvaluator: LocalModelCompatibilityEvaluator
    ): ModelRegistry {
        return ModelRegistry(
            allowlistRepository = allowlistRepository,
            installedModelDao = installedModelDao,
            preferences = preferences,
            compatibilityEvaluator = compatibilityEvaluator
        )
    }

    @Provides
    @Singleton
    fun provideDownloadIntegrityValidator(): DownloadIntegrityValidator {
        return DownloadIntegrityValidator()
    }

    @Provides
    @Singleton
    fun provideModelDownloadCoordinator(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
        preferences: AppPreferences,
        allowlistRepository: AllowlistRepository,
        installedModelDao: InstalledModelDao,
        integrityValidator: DownloadIntegrityValidator,
        notificationCoordinator: NotificationCoordinator
    ): ModelDownloadCoordinator {
        return ModelDownloadCoordinator(
            context = context,
            httpClient = httpClient,
            preferences = preferences,
            allowlistRepository = allowlistRepository,
            installedModelDao = installedModelDao,
            integrityValidator = integrityValidator,
            notificationCoordinator = notificationCoordinator
        )
    }

    @Provides
    @Singleton
    fun provideLocalModelImportCoordinator(): LocalModelImportCoordinator {
        return StubLocalModelImportCoordinator()
    }

    @Provides
    @Singleton
    fun provideLocalModelRepository(
        allowlistRepository: AllowlistRepository,
        modelRegistry: ModelRegistry,
        downloadCoordinator: ModelDownloadCoordinator,
        importCoordinator: LocalModelImportCoordinator,
        telemetry: InMemoryLocalRuntimeTelemetry,
        runtimeManager: ModelRuntimeManager
    ): LocalModelRepository {
        return LocalModelRepository(
            allowlistRepository = allowlistRepository,
            modelRegistry = modelRegistry,
            downloadCoordinator = downloadCoordinator,
            importCoordinator = importCoordinator,
            telemetry = telemetry,
            runtimeManager = runtimeManager
        )
    }

    @Provides
    @Singleton
    fun provideLocalInferenceClient(): LocalInferenceClient {
        return LocalInferenceClient()
    }

    @Provides
    @Singleton
    fun provideDownloadedModelInferenceClient(
        modelRegistry: ModelRegistry,
        runtimeManager: ModelRuntimeManager,
        telemetry: InMemoryLocalRuntimeTelemetry
    ): DownloadedModelInferenceClient {
        return DownloadedModelInferenceClient(
            modelRegistry = modelRegistry,
            runtimeManager = runtimeManager,
            telemetry = telemetry
        )
    }

    @Provides
    @Singleton
    fun provideRemoteInferenceClient(httpClient: OkHttpClient): RemoteInferenceClient {
        return RemoteInferenceClient(httpClient)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        database: AppDatabase,
        preferences: AppPreferences,
        localModelRepository: LocalModelRepository,
        localInferenceClient: LocalInferenceClient,
        downloadedInferenceClient: DownloadedModelInferenceClient,
        remoteInferenceClient: RemoteInferenceClient
    ): ChatRepository {
        return ChatRepository(
            database = database,
            preferences = preferences,
            localModelRepository = localModelRepository,
            localInferenceClient = localInferenceClient,
            downloadedInferenceClient = downloadedInferenceClient,
            remoteInferenceClient = remoteInferenceClient
        )
    }

    private const val BUNDLED_ALLOWLIST_ASSET = "model_allowlist_2_0_3.json"
    private const val BUNDLED_ALLOWLIST_VERSION = "2_0_3"
    private const val REMOTE_ALLOWLIST_URL = ""
}
