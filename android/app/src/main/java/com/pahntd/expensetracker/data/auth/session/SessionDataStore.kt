package com.pahntd.expensetracker.data.auth.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore

/** File name of the single Proto DataStore that persists the authentication [Session]. */
private const val SESSION_DATA_STORE_FILE_NAME = "session.pb"

/**
 * The one application-level Proto DataStore for the authentication [Session].
 *
 * The [dataStore] delegate guarantees exactly one active [DataStore] per process for
 * [SESSION_DATA_STORE_FILE_NAME]; nothing else should open a DataStore on that file.
 * Consumers inject `DataStore<Session>` (see `di/DataStoreModule`) rather than touching this
 * property directly.
 */
val Context.sessionDataStore: DataStore<Session> by dataStore(
    fileName = SESSION_DATA_STORE_FILE_NAME,
    serializer = SessionSerializer,
)
