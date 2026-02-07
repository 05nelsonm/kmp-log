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
package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException

@Throws(IOException::class)
internal actual fun File.openDirectory(): Directory {
    val opener = DirectoryOpener.INSTANCE ?: return Directory.NoOp
    return opener.open(this)
}

internal sealed class DirectoryOpener {

    @Throws(IOException::class)
    internal abstract fun open(dir: File): Directory

    internal companion object {

        @get:JvmSynthetic
        internal val INSTANCE: DirectoryOpener? by lazy {
            DirectoryOpenerAndroid.getOrNull()?.let { return@lazy it }

            val isAvailable = try {
                Class.forName("java.nio.file.Files") != null
            } catch (_: Throwable) {
                false
            }
            if (!isAvailable) return@lazy null

            DirectoryOpenerNioPosix.getOrNull()
        }
    }
}
