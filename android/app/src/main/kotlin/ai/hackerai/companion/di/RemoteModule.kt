package ai.hackerai.companion.di

import ai.hackerai.companion.ExternalControlPrefs
import ai.hackerai.companion.remote.BinderCallingIdentity
import ai.hackerai.companion.remote.CallingIdentitySource
import ai.hackerai.companion.remote.RemoteCommandGate
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteBindModule {
    @Binds
    @Singleton
    abstract fun bindCallingIdentity(impl: BinderCallingIdentity): CallingIdentitySource
}

@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {
    @Provides
    @Singleton
    fun provideRemoteCommandGate(prefs: ExternalControlPrefs): RemoteCommandGate =
        // Seed from prefs (default OFF). Settings / import keep prefs and this flag in sync.
        RemoteCommandGate(externalControlEnabled = prefs.enabled)
}
