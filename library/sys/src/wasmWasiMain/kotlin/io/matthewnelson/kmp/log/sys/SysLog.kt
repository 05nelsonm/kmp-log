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

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.sys.internal.SYS_LOG_UID
import io.matthewnelson.kmp.log.sys.internal.commonFormatLogOrNull
import io.matthewnelson.kmp.log.sys.internal.commonIsInstalled
import io.matthewnelson.kmp.log.sys.internal.commonOf
import io.matthewnelson.kmp.log.sys.internal.wasiDateTime
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

// wasmWasi
public actual open class SysLog private actual constructor(
    min: Level /* = Level.Debug */,
): Log(UID, min) {

    public actual companion object Default: SysLog() {

        public actual const val UID: String = SYS_LOG_UID

        public actual val isInstalled: Boolean get() = commonIsInstalled()

        public actual fun of(
            min: Level,
        ): SysLog = ::SysLog.commonOf(min)

        private const val STDOUT_FILENO: Int = 1
        private const val STDERR_FILENO: Int = 2
    }

    actual final override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        val formatted = run {
            val dateTime = wasiDateTime()
            commonFormatLogOrNull(level, domain, tag, msg, t, dateTime, omitLastNewLine = false) ?: return false
        }.toString().encodeToByteArray()

        val fd = when (level) {
            Level.Verbose,
            Level.Debug,
            Level.Info -> STDOUT_FILENO
            Level.Warn,
            Level.Error,
            Level.Fatal -> STDERR_FILENO
        }

        @OptIn(UnsafeWasmMemoryApi::class)
        withScopedMemoryAllocator { alloc ->
            val data = alloc.allocate(formatted.size)
            for (i in formatted.indices) {
                (data + i).storeByte(formatted[i])
            }
            val iovec = alloc.allocate(Int.SIZE_BYTES * 2)
            iovec.storeInt(data.address.toInt())
            (iovec + Int.SIZE_BYTES).storeInt(formatted.size)
            val result = alloc.allocate(Int.SIZE_BYTES)

            val ret = fdWrite(
                fd = fd,
                iovecPtr = iovec.address.toInt(),
                iovecSize = 1,
                resultPtr = result.address.toInt(),
            )

            if (ret != 0) return false
            return result.loadInt() == formatted.size
        }
    }

    actual final override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        return super.isLoggable(level, domain, tag)
    }
}

@Suppress("OPT_IN_USAGE")
@WasmImport("wasi_snapshot_preview1", "fd_write")
private external fun fdWrite(fd: Int, iovecPtr: Int, iovecSize: Int, resultPtr: Int): Int
