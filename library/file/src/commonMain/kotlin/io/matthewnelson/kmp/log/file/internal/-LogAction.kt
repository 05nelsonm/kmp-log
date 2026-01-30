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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.file.FileStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * An action to be processed.
 * */
internal sealed interface LogAction {

    @Throws(CancellationException::class, Throwable::class)
    suspend operator fun invoke(
        stream: FileStream.ReadWrite,
        buf: ByteArray,
        sizeLog: Long,
        processedWrites: Int,
    ): Long

    /**
     * An action related to log rotation. No actual write functionality is had;
     * it is simply for weaving into the loop. Should always return `0`, or
     * [EXECUTE_ROTATE_LOGS].
     * */
    fun interface Rotation: LogAction {

        companion object {
            // Special value for passing via processedWrites parameter to indicate
            // that this Rotation action is being consumed and ignored. Serves a
            // double purpose in that if the Rotation action is for lazily closing
            // a LockFile due to FileLock release failure, the negative value will
            // not trigger a FileStream.sync call.
            internal const val CONSUME_AND_IGNORE = -615
        }
    }

    /**
     * An action whereby data is to be written to the log. If the action is to be
     * dropped, either from error or channel closure, [drop] **MUST** be called.
     *
     * If [EXECUTE_ROTATE_LOGS] is returned by [invoke], then this [Write] action
     * will be cached and subsequently retried after log rotation has been executed.
     * */
    interface Write: LogAction {

        @Throws(IllegalStateException::class)
        fun drop(warn: Boolean)
    }

    companion object {
        // Special return value to trigger FileLog.rotateLogs
        internal const val EXECUTE_ROTATE_LOGS = -42L

        // To prevent infinite loops. In the unlikely event a log rotation
        // results in a lost lock for the log file and another process writes
        // to it before we are able to re-acquire it, and then the log rotation
        // is needed AGAIN. If the value is exceeded, LogAction.Write will
        // simply write its log to the stream and move on.
        internal const val MAX_RETRIES = 3

        internal inline fun LogAction?.drop(warn: Boolean) {
            (this as? Write)?.drop(warn)
        }
    }
}
