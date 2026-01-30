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

import io.matthewnelson.kmp.file.use
import io.matthewnelson.kmp.log.file.withTmpFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class LockFileLockFileUnitTest {

    @Test
    fun givenLockedFile_whenRangesHeldByAnotherProcess_thenLockNonBlockFailsDueToTimeout() = runTest {
        if (TEST_FILE_LOCK_JAR.isEmpty()) {
            println("Skipping...")
            return@runTest
        }

        withTmpFile { tmp ->
            tmp.openLockFile().use { lockFile ->
                val position = 0L
                val size = 30L

                execTestFileLockJar(tmp, 2_000, position, size).createProcess().use { p ->
                    var acquired: Boolean? = null
                    p.stdoutFeed { line ->
                        when {
                            line == null -> return@stdoutFeed
                            line.startsWith("ACQUIRED") -> acquired = true
                            line.startsWith("FAILURE") -> acquired = false
                        }
                        println(line)
                    }.stderrFeed { line ->
                        println(line ?: return@stderrFeed)
                    }

                    while (acquired == null && p.isAlive) {
                        p.waitFor(5.milliseconds)
                    }

                    assertEquals(true, acquired, "test-file-lock.jar failed to acquire the lock as expected...")

                    withContext(Dispatchers.IO) {
                        try {
                            lockFile.lockNonBlock(position, size, timeout = 50.milliseconds)
                            fail("lockNonBlock did not timeout as expected...")
                        } catch (t: Throwable) {
                            assertIs<TimeoutCancellationException>(t.cause)
                        }
                    }
                }
            }
        }
    }
}
