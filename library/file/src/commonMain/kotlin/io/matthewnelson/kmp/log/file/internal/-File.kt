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

import io.matthewnelson.kmp.file.AccessDeniedException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileStream
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.OpenExcl
import io.matthewnelson.kmp.file.chmod2
import io.matthewnelson.kmp.file.openReadWrite

@Throws(IOException::class)
internal inline fun File.openLogFileRobustly(mode: String): FileStream.ReadWrite = try {
    openReadWrite(excl = OpenExcl.MaybeCreate.of(mode))
} catch (e: AccessDeniedException) {
    try {
        chmod2(mode)
    } catch (ee: IOException) {
        e.addSuppressed(ee)
        throw e
    }
    try {
        openReadWrite(excl = OpenExcl.MaybeCreate.of(mode))
    } catch (ee: IOException) {
        e.addSuppressed(ee)
        throw e
    }
}
