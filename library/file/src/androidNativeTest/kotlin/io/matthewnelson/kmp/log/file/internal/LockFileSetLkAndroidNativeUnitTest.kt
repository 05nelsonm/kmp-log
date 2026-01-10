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
@file:Suppress("DUPLICATE_LABEL_IN_WHEN")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.exists2
import io.matthewnelson.kmp.file.toFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.EAGAIN
import platform.posix.EWOULDBLOCK
import platform.posix.close
import platform.posix.errno
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalForeignApi::class)
class LockFileSetLkAndroidNativeUnitTest {

    @Test
    fun givenLockedFile_whenRangesHeldByAnotherProcess_thenSetlkFunctionsAsExpected() {
        // When this test runs in :library:sys via androidInstrumentedTest,
        // it will open and lock a file before executing the test binary in
        // within emulator.
        val path = getenv("io_matthewnelson_kmp_log_file_test_setlk_path")
            ?.toKString()

        if (path.isNullOrBlank()) {
            println("Skipping...")
            return
        } else {
            println("CHECKING")
        }

        val file = path.toFile()
        assertTrue(file.exists2(), "exists")

        val fd = LockFile.openFd(file)
        try {
            assertEquals(
                -1,
                kmp_log_file_setlk(fd, position = 0, length = 1, locking = 1, blocking = 0, exclusive = 1),
                "[0,1]",
            )
            when (errno) {
                EWOULDBLOCK, EAGAIN -> {} // pass
                else -> fail("blocking = 0 >> ${errnoToIOException(errno).message}")
            }
            assertEquals(
                -1,
                kmp_log_file_setlk(fd, position = Long.MAX_VALUE - 1, length = 1, locking = 1, blocking = 0, exclusive = 1),
                "[(Long.MAX_VALUE - 1),1]",
            )
            when (errno) {
                EWOULDBLOCK, EAGAIN -> {} // pass
                else -> fail("blocking = 0 >> ${errnoToIOException(errno).message}")
            }

            // We can lock & unlock a different range that is available
            assertEquals(
                0,
                kmp_log_file_setlk(fd, position = 2, length = 1, locking = 1, blocking = 0, exclusive = 1),
                "WRLCK[2, 1]",
            )
            assertEquals(
                0,
                kmp_log_file_setlk(fd, position = 2, length = 1, locking = 0, blocking = 0, exclusive = 1),
                "UNLCK[2, 1]",
            )
        } finally {
            if (close(fd) != 0) errnoToIOException(errno)
        }
    }
}
