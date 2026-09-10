package com.pahntd.expensetracker.di

import android.content.Context
import androidx.datastore.core.DataStore
import com.pahntd.expensetracker.data.auth.session.Session
import com.pahntd.expensetracker.data.auth.session.sessionDataStore
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
     * Exposes the single application-level session Proto DataStore for injection.
     * Backed by `Context.sessionDataStore`, so this returns that one instance rather
     * than creating another.
     */
    @Provides
    @Singleton
    fun provideSessionDataStore(
        @ApplicationContext context: Context
    ): DataStore<Session> = context.sessionDataStore
}
