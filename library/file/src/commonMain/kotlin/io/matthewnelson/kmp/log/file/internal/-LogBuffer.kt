/*
 * Copyright (c) 2026 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
@file:Suppress("NOTHING_TO_INLINE", "WRONG_INVOCATION_KIND", "LocalVariableName")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.encoding.core.EncoderDecoder.Companion.DEFAULT_BUFFER_SIZE
import io.matthewnelson.kmp.file.FileStream
import io.matthewnelson.kmp.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmInline

/*
* An action to be enqueued into LogBuffer for processing.
* */
internal typealias LogAction = suspend (
    // The open log. May be null if there was an error and this
    // action is being dropped (see consumeAndIgnore).
    stream: FileStream.ReadWrite?,

    // A pre-allocated buffer to use for UTF-8 encoding.
    buf: ByteArray,

    // The current size of the log file being written to. This
    // is utilized to trigger a log rotation + retry, in the
    // event that writing a log entry would cause it to exceed
    // the configured maxLogSize.
    //
    // NOTE: This should be reflective of stream.size(), but is
    // a local value tracked by the log loop and updated with
    // LogAction return values. This is in order to mitigate
    // repeated (unnecessary) calls to stream.size().
    sizeLog: Long,

    // The number of LogActions prior to this one which resulted
    // in data written to the log (i.e. had a return of > 0L).
    //
    // LogActions are processed in chunks whereby the log loop
    // will sync then yield to another process, and then continue
    // processing more LogAction, so.
    processed: Int,

// Return value is the number of bytes written to the stream, or
// 0L to indicate no write occurred.
//
// There are special negative return values that are utilized to
// trigger log rotations. See LogBuffer.Companion
) -> Long

internal suspend inline fun LogAction.consumeAndIgnore(buf: ByteArray, sizeLog: Long = 0L, processed: Int = 0) {
    try {
        invoke(null, buf, sizeLog, processed)
    } catch (_: Throwable) {}
}

@JvmInline
internal value class LogBuffer private constructor(internal val channel: Channel<LogAction>) {

    internal constructor(): this(Channel(
        capacity = UNLIMITED,
        // This "should" NEVER happen because LogBuffer.use is
        // utilized in the FileLog.onInstall LogJob, so.
        onUndeliveredElement = { logAction ->
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
                logAction.consumeAndIgnore(EMPTY_BUF)
            }
        }
    ))

    @Throws(IllegalArgumentException::class)
    internal constructor(capacity: Int, LOG: Log.Logger?, scope: CoroutineScope): this(Channel(
        capacity = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { logAction ->
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(start = CoroutineStart.ATOMIC) {
                logAction.consumeAndIgnore(EMPTY_BUF)
                LOG?.w { "LogBuffer[capacity=$capacity] exceeded. Oldest log has been dropped." }
            }
        }
    )) {
        require(capacity > 0) { "capacity < 1" }
        require(capacity != UNLIMITED) { "capacity == Channel.UNLIMITED" }
    }

    internal companion object {

        // Special return value for a LogAction to trigger rotateLogs
        internal const val EXECUTE_ROTATE_LOGS = -42L

        // Special return value for a LogAction to trigger rotateLogs
        // and then retry the LogAction again. See FileLog.log().
        internal const val EXECUTE_ROTATE_LOGS_AND_RETRY = -615L

        // To prevent infinite loops. In the unlikely event a log rotation
        // results in a lost lock for the log file and another process writes
        // to it before we are able to re-acquire it, and then the log rotation
        // is needed AGAIN. If the value is exceeded, LogAction produced by
        // FileLog.log() will simply write its log and move on.
        internal const val MAX_RETRIES = 3

        private val EMPTY_BUF = ByteArray(0)
    }
}

@OptIn(ExperimentalContracts::class)
internal suspend inline fun LogBuffer.use(LOG: Log.Logger?, block: LogBuffer.(buf: ByteArray) -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
    var threw: Throwable? = null

    try {
        block(buf)
    } catch (t: Throwable) {
        threw = t
    } finally {
        channel.close()
        var count = 0L
        while (true) {
            val logAction = channel.tryReceive().getOrNull() ?: break
            count++
            logAction.consumeAndIgnore(buf)
        }
        if (LOG != null && count > 0L) {
            LOG.w { "Skipped $count logs" }
        }
        buf.fill(0)
    }

    threw?.let { throw it }
    return
}
