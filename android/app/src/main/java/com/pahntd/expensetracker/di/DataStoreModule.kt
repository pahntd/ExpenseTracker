package com.pahntd.expensetracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import com.pahntd.expensetracker.data.auth.session.Session
import com.pahntd.expensetracker.data.auth.session.SessionKeysetManager
import com.pahntd.expensetracker.data.auth.session.SessionSerializer
import com.pahntd.expensetracker.data.auth.session.createSessionDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * Encrypting [SessionSerializer], wired to the AEAD from [SessionKeysetManager]
     * (Tink keyset wrapped by an Android Keystore master key).
     */
    @Provides
    @Singleton
    fun provideSessionSerializer(
        keysetManager: SessionKeysetManager
    ): SessionSerializer = SessionSerializer { keysetManager.aead }

    /**
     * The single application-level session Proto DataStore exposed to the rest of the app.
     * Still just `DataStore<Session>` to callers — encryption is transparent.
     */
    @Provides
    @Singleton
    fun provideSessionDataStore(
        @ApplicationContext context: Context,
        serializer: SessionSerializer,
    ): DataStore<Session> = createSessionDataStore(context, serializer)
}
