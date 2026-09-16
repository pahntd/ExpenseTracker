package com.pahntd.expensetracker.di

import android.content.Context
import com.pahntd.expensetracker.data.network.NetworkMonitor
import com.pahntd.expensetracker.data.network.NetworkMonitorImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkMonitorModule {

    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
    ): NetworkMonitor = NetworkMonitorImpl(context, applicationScope)
}
