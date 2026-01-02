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

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.use
import io.matthewnelson.kmp.log.file.withTmpFile
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.EAGAIN
import platform.posix.EINVAL
import platform.posix.EOVERFLOW
import platform.posix.EWOULDBLOCK
import platform.posix.F_UNLCK
import platform.posix.F_WRLCK
import platform.posix.errno
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

/**
 * Currently `kmp-process` does not have support for Native/Windows. Have to
 * reconfigure to a base test abstraction once that changes.
 * */
@OptIn(ExperimentalForeignApi::class)
class LockFileSetLkUnixUnitTest: LockFileNativeBaseTest<Int>() {

    override fun File.open(): Int {
        return LockFile.openFd(this)
    }

    override fun Int.close() {
        if (platform.posix.close(this) != 0) throw errnoToIOException(errno)
    }

    @Test
    fun givenFileDescriptor_whenInvalidPositionOrSize_thenFailsAsExpected() = withTmpFile { tmp ->
        val fd = tmp.open()
        try {
            assertEquals(-1, kmp_log_file_setlk(fd, type = F_WRLCK, blocking = 1, position = -1L, size = 1L))
            assertEquals(EINVAL, errno, "position == -1L [EINVAL]")
            assertEquals(-1, kmp_log_file_setlk(fd, type = F_WRLCK, blocking = 1, position = 0L, size = -1L))
            assertEquals(EINVAL, errno, "size == -1L [EINVAL]")
            assertEquals(-1, kmp_log_file_setlk(fd, type = F_WRLCK, blocking = 1, position = Long.MAX_VALUE - 5, size = 10L))
            assertEquals(EOVERFLOW, errno, "position + size is negative [EOVERFLOW]")
        } finally {
            fd.close()
        }
    }

    @Test
    fun givenFileDescriptor_whenLockAndUnlock_thenFunctionsAsExpected() = withTmpFile { tmp ->
        if (TEST_FILE_LOCK_JAR.isEmpty()) {
            println("Skipping...")
            return@withTmpFile
        }

        val fd = tmp.open()
        try {
            // Acquiring a lock should inhibit other processes from locking it with the range.
            assertEquals(0, kmp_log_file_setlk(fd, F_WRLCK, blocking = 0, 1L, 0L))

            var out = runJarOutput(tmp, 150, 1, 3)
            if (!out.stdout.startsWith("FAILURE")) {
                println(out)
                println(out.stdout)
                println(out.stderr)
                fail("test-file-lock.jar acquired the lock when it shouldn't have been able to...")
            }

            // Unlocking it should allow other processes to acquire the lock.
            assertEquals(0, kmp_log_file_setlk(fd, F_UNLCK, blocking = 0, 1L, 0L))

            out = runJarOutput(tmp, 150, 1, 3)
            if (!out.stdout.startsWith("ACQUIRED")) {
                println(out)
                println(out.stdout)
                println(out.stderr)
                fail("test-file-lock.jar failed to acquire the lock when it should have been able to...")
            }

            // If another process holds the lock, we should fail with EWOULDBLOCK when blocking = 0 (false)
            runJar(tmp, 500, 0, 3).createProcess().use { p ->
                var acquired: Boolean? = null
                var releasing = false
                var released = false

                p.stdoutFeed { line ->
                    when {
                        line == null -> {}
                        line.startsWith("ACQUIRED") -> acquired = true
                        line.startsWith("RELEASING") -> releasing = true
                        line.startsWith("RELEASED") -> released = true
                        line.startsWith("FAILURE") -> acquired = false
                        else -> println(line)
                    }
                }.stderrFeed { line ->
                    println(line ?: return@stderrFeed)
                }

                while (acquired == null) {
                    p.waitFor(5.milliseconds)
                }

                assertEquals(true, acquired, "test-file-lock.jar failed to acquire the lock as expected...")

                assertEquals(-1, kmp_log_file_setlk(fd, F_WRLCK, blocking = 0, 0L, 1L))

                when (errno) {
                    EWOULDBLOCK, EAGAIN -> {} // pass
                    else -> fail("blocking = 0 >> ${errnoToIOException(errno).message}")
                }

                assertTrue( p.isAlive, "p.isAlive")
                assertFalse(releasing, "releasing")

                // Should now block until test-file-lock.jar times out and the process stops.
                assertEquals(0, kmp_log_file_setlk(fd, F_WRLCK, blocking = 1, 0L, 1L))

                assertTrue(releasing, "test-file-lock.jar did not dispatch RELEASING as expected..")
                assertFalse(released, "released")
                assertTrue(p.isAlive, "p.isAlive")
            }
        } finally {
            fd.close()
        }
    }

    private fun runJar(
        tmp: File,
        timeoutMs: Long,
        position: Long,
        size: Long,
    ): Process.Builder = Process.Builder(command = "java")
        .args("-jar")
        .args(TEST_FILE_LOCK_JAR)
        .args(tmp.path)
        .args(timeoutMs.toString())
        .args(position.toString())
        .args(size.toString())
        .stdin(Stdio.Null)

    private fun runJarOutput(
        tmp: File,
        timeoutMs: Long,
        position: Long,
        size: Long,
    ): Output = runJar(tmp, timeoutMs, position, size).createOutput {
        timeoutMillis = (timeoutMs + 500).toInt()
    }
}
