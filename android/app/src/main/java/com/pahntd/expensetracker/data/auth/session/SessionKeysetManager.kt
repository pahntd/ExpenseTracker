package com.pahntd.expensetracker.data.auth.session

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Tink AEAD keyset that encrypts the session DataStore.
 *
 * Key hierarchy (Tink's recommended Android setup):
 *  - A data-encryption keyset (AES256-GCM) is kept in a private SharedPreferences file
 *    ([KEYSET_PREF_FILE] / [KEYSET_NAME]). It is generated on first use.
 *  - That keyset is itself encrypted ("key wrapping") by a master key that lives in the
 *    Android Keystore under [MASTER_KEY_URI]. The master key is non-exportable and never
 *    touches app-readable storage.
 *
 * Nothing here is a secret literal: only the Keystore alias and pref names are hard-coded.
 */
@Singleton
class SessionKeysetManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * The AEAD primitive for the session store. Built lazily on first read/write (DataStore
     * runs those on `Dispatchers.IO`) and reused for the process lifetime.
     */
    val aead: Aead by lazy { createAead() }

    private fun createAead(): Aead {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        return keysetHandle.getPrimitive(Aead::class.java)
    }

    private companion object {
        /** Android Keystore alias used to wrap the session keyset. */
        const val MASTER_KEY_URI = "android-keystore://expense_tracker_session_master_key"
        const val KEYSET_NAME = "session_datastore_keyset"
        const val KEYSET_PREF_FILE = "session_datastore_keyset_prefs"
    }
}
