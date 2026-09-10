package com.pahntd.expensetracker.data.auth.session

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile

/** File backing the single session Proto DataStore. Contents are Tink-AEAD encrypted at rest. */
private const val SESSION_DATA_STORE_FILE_NAME = "session.pb"

private const val TAG = "SessionDataStore"

/**
 * Builds the one application-level [DataStore] for the authentication [Session].
 *
 * Called exactly once, from `di/DataStoreModule`; nothing else opens [SESSION_DATA_STORE_FILE_NAME].
 * On a [androidx.datastore.core.CorruptionException] from [SessionSerializer] (failed decryption or
 * protobuf parse) the file is reset to [SessionSerializer.defaultValue] — an empty session — rather
 * than propagating a crash to readers.
 */
internal fun createSessionDataStore(
    context: Context,
    serializer: SessionSerializer,
): DataStore<Session> = DataStoreFactory.create(
    serializer = serializer,
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        Log.w(TAG, "Session store unreadable; resetting to an empty session.", exception)
        serializer.defaultValue
    },
    produceFile = { context.dataStoreFile(SESSION_DATA_STORE_FILE_NAME) },
)
