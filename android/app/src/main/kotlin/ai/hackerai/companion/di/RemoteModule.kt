package ai.hackerai.companion.di

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
    fun provideRemoteCommandGate(): RemoteCommandGate =
        // External control stays OFF by default (REMOTE_CONTROL.md / UNIFIED_BACKUP.md).
        RemoteCommandGate(externalControlEnabled = false)
}
