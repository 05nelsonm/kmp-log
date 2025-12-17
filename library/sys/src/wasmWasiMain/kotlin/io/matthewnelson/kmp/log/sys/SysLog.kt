/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.log.sys

import io.matthewnelson.encoding.core.Decoder.Companion.decodeBuffered
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.sys.internal.SYS_LOG_UID
import io.matthewnelson.kmp.log.sys.internal.commonFormatLogOrNull
import io.matthewnelson.kmp.log.sys.internal.commonIsInstalled
import io.matthewnelson.kmp.log.sys.internal.commonOf
import io.matthewnelson.kmp.log.sys.internal.wasiDateTime
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

// wasmWasi
public actual class SysLog private actual constructor(min: Level): Log(SYS_LOG_UID, min) {

    public actual companion object {

        public actual val Debug: SysLog = SysLog(Level.Debug)

        public actual const val UID: String = SYS_LOG_UID

        public actual val isInstalled: Boolean get() = commonIsInstalled()

        public actual fun of(
            min: Level,
        ): SysLog = ::SysLog.commonOf(min)

        private const val STDOUT_FILENO: Int = 1
        private const val STDERR_FILENO: Int = 2
    }

    actual override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        val formatted = run {
            val dateTime = wasiDateTime()
            commonFormatLogOrNull(level, domain, tag, msg, t, dateTime, omitLastNewLine = false)
        } ?: return false

        val fd = when (level) {
            Level.Verbose,
            Level.Debug,
            Level.Info -> STDOUT_FILENO
            Level.Warn,
            Level.Error,
            Level.Fatal -> STDERR_FILENO
        }

        return try {
            writeLog(formatted, fd)
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    actual override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return super.isLoggable(level, domain, tag)
    }
}

@Suppress("OPT_IN_USAGE")
@WasmImport("wasi_snapshot_preview1", "fd_write")
private external fun fdWrite(fd: Int, iovecPtr: Int, iovecSize: Int, resultPtr: Int): Int

// @Throws(IllegalStateException::class)
private fun writeLog(formatted: CharSequence, fd: Int) {
    @OptIn(UnsafeWasmMemoryApi::class)
    withScopedMemoryAllocator { alloc ->
        var data: Pointer? = null
        val iovec = alloc.allocate(Int.SIZE_BYTES * 2)
        val result = alloc.allocate(Int.SIZE_BYTES)

        formatted.decodeBuffered(
            decoder = UTF8,
            throwOnOverflow = false,
            maxBufSize = 1024 * 2,
            action = { buf, offset, len ->
                // Cannot allocate a Pointer for data until we know the actual size
                // of buf (which will be re-used on every invocation of action).
                //
                // decodeBuffered may use a smaller one than what is defined for
                // maxBufSize if it can one-shot the text to UTF-8 byte transformation.
                //
                // Also, it will never be 0 len because commonFormatLogOrNull will
                // return null instead of an empty StringBuilder, so there is at LEAST 1.
                if (data == null) data = alloc.allocate(buf.size)

                repeat(len) { i -> (data + i).storeByte(buf[offset + i]) }

                var written = 0
                while (written < len) {
                    iovec.storeInt((data + written).address.toInt())
                    (iovec + Int.SIZE_BYTES).storeInt(len - written)

                    val ret = fdWrite(
                        fd = fd,
                        iovecPtr = iovec.address.toInt(),
                        iovecSize = 1,
                        resultPtr = result.address.toInt(),
                    )
                    check(ret == 0) { "ret != 0" }

                    written += result.loadInt()
                }
            }
        )
    }
}
