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
package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.file.withTmpFile
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

// See unixTest/mingwTest source sets
@OptIn(ExperimentalForeignApi::class)
abstract class LockFileUniqueIdNativeBaseTest<Descriptor>: LockFileNativeBaseTest<Descriptor>() {

    protected abstract fun kmpLogFileUniqueId(fd: Descriptor, uniqueId: CPointer<ULongVar>?): Int

    @Test
    fun givenFile_whenOpened_thenNativeKmpLogFileUniqueIdFunctionWorks() = withTmpFile { tmp ->
        val fd = tmp.open()
        try {
            val uniqueId = ULongArray(KMP_LOG_FILE_UNIQUE_ID_SIZE) { (-1L).toULong() }
            val ret = uniqueId.usePinned { pinned ->
                kmpLogFileUniqueId(fd, pinned.addressOf(0))
            }
            assertEquals(0, ret)
            println(uniqueId)
            uniqueId.forEach { l ->
                if (l != (-1L).toULong()) return // Success
            }
            fail("$uniqueId")
        } finally {
            fd.close()
        }
    }

    @Test
    fun givenKmpLogFileUniqueId_whenUniqueIdParameterNull_thenReturnsNeg1() = withTmpFile { tmp ->
        val fd = tmp.open()
        try {
            assertEquals(-1, kmpLogFileUniqueId(fd, null))
        } finally {
            fd.close()
        }
    }
}
