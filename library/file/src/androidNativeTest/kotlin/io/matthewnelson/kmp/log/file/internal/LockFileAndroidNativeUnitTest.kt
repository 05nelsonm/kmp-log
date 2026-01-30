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

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.exists2
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.file.use
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import platform.posix.EAGAIN
import platform.posix.EWOULDBLOCK
import platform.posix.close
import platform.posix.errno
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

private const val ENV_PATH = "io_matthewnelson_kmp_log_file_test_setlk_path"

/**
 * These tests are run via module `:sys` on an emulator, executed as a child process.
 *
 * Prior to process execution, a file is opened by the parent process whose path is
 * then passed in via environment variable [ENV_PATH]. File locks on byte ranges
 * `position = 0, length = 1` and `position = Long.MAX_VALUE - 1, length = 0` are
 * acquired by the parent process so that failure testing can be had here.
 * */
@OptIn(ExperimentalForeignApi::class)
class LockFileAndroidNativeUnitTest {

    @Throws(AssertionError::class)
    private fun testLockFile(): File {
        val file = getenv(ENV_PATH)?.toKString()?.toFile()
            ?: throw AssertionError("getenv($ENV_PATH) == null")
        assertTrue(file.exists2(), "file[$file].exists2 != true")
        return file
    }

    @Test
    fun givenLockedFile_whenRangesHeldByAnotherProcess_thenSetlkFunctionsAsExpected() {
        val file = testLockFile()
        val fd = LockFile.openFd(file)
        try {
            // non-blocking lock acquisition fails on ranges held in parent process.
            assertEquals(
                -1,
                kmp_log_file_setlk(fd, position = 0, length = 1, locking = 1, blocking = 0, exclusive = 1),
                "[0,1]",
            )
            when (errno) {
                EWOULDBLOCK, EAGAIN -> {} // pass
                else -> fail("[0,1] >> ${errnoToIOException(errno).message}")
            }
            assertEquals(
                -1,
                kmp_log_file_setlk(fd, position = Long.MAX_VALUE - 1, length = 0, locking = 1, blocking = 0, exclusive = 1),
                "[(Long.MAX_VALUE - 1),0]",
            )
            when (errno) {
                EWOULDBLOCK, EAGAIN -> {} // pass
                else -> fail("[(Long.MAX_VALUE - 1),0] >> ${errnoToIOException(errno).message}")
            }

            // We can lock & unlock a different range that is available
            assertEquals(
                0,
                kmp_log_file_setlk(fd, position = 2, length = 1, locking = 1, blocking = 0, exclusive = 1),
                "WRLCK[2,1]",
            )
            assertEquals(
                0,
                kmp_log_file_setlk(fd, position = 2, length = 1, locking = 0, blocking = -1, exclusive = -1),
                "UNLCK[2,1]",
            )
        } finally {
            if (close(fd) != 0) errnoToIOException(errno)
        }
    }

    @Test
    fun givenLockedFile_whenRangesHeldByAnotherProcess_thenLockNonBlockFailsDueToTimeoutCancellation() = runTest {
        testLockFile().openLockFile().use { lockFile ->
            withContext(Dispatchers.IO) {
                try {
                    lockFile.lockNonBlock(timeout = 50.milliseconds, position = 0, size = 1)
                    fail("lockNonBlock did not timeout as expected...")
                } catch (e: IOException) {
                    assertIs<TimeoutCancellationException>(e.cause)
                }
            }
        }
    }
}
