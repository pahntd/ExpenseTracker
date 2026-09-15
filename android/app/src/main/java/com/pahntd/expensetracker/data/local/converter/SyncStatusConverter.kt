package com.pahntd.expensetracker.data.local.converter

import androidx.room.TypeConverter

class SyncStatusConverter {

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String {
        return status.name
    }

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return SyncStatus.valueOf(value)
    }
}
