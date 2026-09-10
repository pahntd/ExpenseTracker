package com.pahntd.expensetracker.data.auth.session

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/**
 * [Serializer] for the [Session] proto backing the session [androidx.datastore.core.DataStore].
 *
 * Step 1: plain protobuf serialization only. Encryption (Tink / Android Keystore) is added in a
 * later step and will wrap the streams handled here.
 *
 * A byte stream that cannot be parsed as a [Session] is reported as a [CorruptionException], which
 * is the failure signal Proto DataStore expects; it lets a `corruptionHandler` (or the default
 * behaviour of replacing the file with [defaultValue]) recover instead of crashing on every read.
 */
object SessionSerializer : Serializer<Session> {

    override val defaultValue: Session = Session.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Session =
        try {
            Session.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read session proto.", exception)
        }

    override suspend fun writeTo(t: Session, output: OutputStream) {
        t.writeTo(output)
    }
}
