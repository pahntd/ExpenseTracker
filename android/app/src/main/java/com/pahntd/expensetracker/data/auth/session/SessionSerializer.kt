package com.pahntd.expensetracker.data.auth.session

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

/**
 * [Serializer] for the [Session] proto. Encryption lives entirely in this storage layer:
 *
 *  write:  Session -> protobuf bytes -> Tink AEAD encrypt -> file
 *  read:   file -> Tink AEAD decrypt -> protobuf parse -> Session
 *
 * The [Session] schema is unchanged and callers keep working with plain [Session] objects.
 *
 * [aead] is supplied as a lambda so the Tink keyset / Android Keystore access is deferred to
 * the first actual read or write (which DataStore performs off the main thread) and so this
 * class stays unit-testable with a plain in-memory AEAD.
 *
 * Failure handling follows DataStore's contract: unreadable data (failed AEAD decryption or a
 * non-parseable payload) is surfaced as [CorruptionException], which the DataStore's
 * `corruptionHandler` turns into a reset to [defaultValue] instead of a crash. A corrupt store
 * therefore reads back as an empty session — never as arbitrary/partial token data.
 */
class SessionSerializer(
    private val aead: () -> Aead,
) : Serializer<Session> {

    override val defaultValue: Session = Session.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Session {
        val ciphertext = input.readBytes()
        // No bytes yet (fresh or truncated file) -> treat as "no session".
        if (ciphertext.isEmpty()) return defaultValue

        val plaintext = try {
            aead().decrypt(ciphertext, ASSOCIATED_DATA)
        } catch (exception: GeneralSecurityException) {
            throw CorruptionException("Unable to decrypt session store.", exception)
        }

        return try {
            Session.parseFrom(plaintext)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Unable to parse decrypted session proto.", exception)
        }
    }

    override suspend fun writeTo(t: Session, output: OutputStream) {
        val ciphertext = aead().encrypt(t.toByteArray(), ASSOCIATED_DATA)
        output.write(ciphertext)
    }

    private companion object {
        /**
         * AEAD associated data: a fixed, non-secret domain tag bound to the ciphertext and
         * checked on decrypt. This is NOT an IV/nonce — Tink generates a fresh random nonce
         * for every [Aead.encrypt] call internally.
         */
        val ASSOCIATED_DATA: ByteArray = "expense_tracker_session".toByteArray(Charsets.UTF_8)
    }
}
