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
package io.matthewnelson.kmp.log.file

import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.readUtf8
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.Log.Level
import io.matthewnelson.kmp.log.file.internal.SynchronizedLock
import io.matthewnelson.kmp.log.file.internal.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FileLogUnitTest {

    @Test
    fun givenBlacklistDomains_whenIsLoggable_thenReturnsExpected() = runTest {
        withTmpFile { tmp ->
            val log = FileLog.Builder(tmp.path)
                .blacklistDomain("blacklist.domain")
                .build()

            log.installAndTest {
                assertFalse(Log.Logger.of(tag = "Tag", domain = "blacklist.domain").isLoggable(Level.Error))
                assertTrue(Log.Logger.of(tag = "Tag", domain = "not.blacklist.domain").isLoggable(Level.Error))
                assertTrue(Log.Logger.of(tag = "Tag", domain = null).isLoggable(Level.Error))
            }
        }
    }

    @Test
    fun givenWhitelistDomains_whenIsLoggable_thenReturnsExpected() = runTest {
        withTmpFile { tmp ->
            val log = FileLog.Builder(tmp.path)
                .whitelistDomain("kmp.log")
                .whitelistDomainNull(allow = false)
                .build()

            log.installAndTest {
                assertFalse(Log.Logger.of(tag = "Tag", domain = null).isLoggable(Level.Error))
                assertTrue(Log.Logger.of(tag = "Tag", domain = "kmp.log").isLoggable(Level.Error))
            }
        }
    }

    @Test
    fun givenWhitelistDomainNull_whenIsLoggable_thenReturnsExpected() = runTest {
        withTmpFile { tmp ->
            val log = FileLog.Builder(tmp.path)
                .whitelistDomain("kmp.log")
                .whitelistDomainNull(allow = true)
                .build()

            log.installAndTest {
                assertTrue(Log.Logger.of(tag = "Tag", domain = null).isLoggable(Level.Error))
                assertTrue(Log.Logger.of(tag = "Tag", domain = "kmp.log").isLoggable(Level.Error))
            }
        }
    }

    @Test
    fun givenWhitelistTags_whenIsLoggable_thenReturnsExpected() = runTest {
        withTmpFile { tmp ->
            val log = FileLog.Builder(tmp.path)
                .whitelistTag("Tag")
                .build()

            log.installAndTest {
                assertTrue(Log.Logger.of(tag = "Tag", domain = null).isLoggable(Level.Error))
                assertTrue(Log.Logger.of(tag = "Tag", domain = "kmp.log").isLoggable(Level.Error))
                assertFalse(Log.Logger.of(tag = "NotTag").isLoggable(Level.Error))
            }
        }
    }

    @Test
    fun givenWhitelistDomainsAndTags_whenIsLoggable_thenReturnsExpected() = runTest {
        withTmpFile { tmp ->
            val log = FileLog.Builder(tmp.path)
                .whitelistDomain("kmp.log")
                .whitelistDomainNull(allow = false)
                .whitelistTag("Tag")
                .build()

            log.installAndTest {
                assertFalse(Log.Logger.of(tag = "Tag", domain = null).isLoggable(Level.Error))
                assertTrue(Log.Logger.of(tag = "Tag", domain = "kmp.log").isLoggable(Level.Error))
                assertFalse(Log.Logger.of(tag = "NotTag", domain = "kmp.log").isLoggable(Level.Error))
            }
        }
    }

    @Test
    fun givenPendingLogCount_whenUninstalled_thenEquals0() = runTest {
        withTmpFile { tmp ->
            val log = FileLog.Builder(tmp.path).build()
            log.installAndTest {
                assertTrue(log.uninstallAndAwaitAsync())

                // Confirms that a log was ingested and pendingLogCount had been incremented.
                assertTrue(log.files[0].readUtf8().isNotBlank())
            }
            assertEquals(0, log.pendingLogCount)
        }
    }

    @Test
    fun givenPendingLogCount_whenPreProcessingFormatNull_thenWasDecremented() = runTest {
        withTmpFile { tmp ->
            val log = FileLog.Builder(tmp.path).build()
            log.installAndTest {
                val logger = Log.Logger.of("PendingLogCount.Error")

                // Should not have been logged b/c empty lines, so preprocessing
                // would have returned null.
                assertEquals(0, logger.w("\n\n"))

                assertTrue(log.uninstallAndAwaitAsync())

                // Confirms that a log was ingested and pendingLogCount had been incremented.
                assertTrue(log.files[0].readUtf8().isNotBlank())
            }
            assertEquals(0, log.pendingLogCount)
        }
    }

    @Test
    fun givenOpenedCloseables_whenUninstalled_thenAreAllClosedProperly() = runTest {
        withTmpFile { tmp ->
            val log = FileLog.Builder(tmp.path)
                .debug(enable = true)
                .build()

            // CloseableCoroutineDispatcher allocations
            val allocated = mutableListOf<String>()
            val deallocated = mutableListOf<String>()

            // Closeable files
            val opened = mutableListOf<String>()
            val closed = mutableListOf<String>()

            val closeChecker = object : AbstractTestLog(uid = "CloseChecker") {

                private val lock = SynchronizedLock()

                override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
                    super.log(level, domain, tag, msg, t)

                    if (msg == null) return false
                    if (msg.startsWith("Allocated >> ")) {
                        synchronized(lock) {
                            allocated.add(msg.substringAfter("Allocated >> "))
                        }
                        return true
                    }
                    if (msg.startsWith("Deallocated >> ")) {
                        synchronized(lock) {
                            deallocated.add(msg.substringAfter("Deallocated >> "))
                        }
                        return true
                    }
                    if (msg.startsWith("Opened >> ")) {
                        synchronized(lock) {
                            opened.add(msg.substringAfter("Opened >> "))
                        }
                        return true
                    }
                    if (msg.startsWith("Closed >> ")) {
                        synchronized(lock) {
                            closed.add(msg.substringAfter("Closed >> "))
                        }
                        return true
                    }
                    return false
                }

                override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
                    if (!super.isLoggable(level, domain, tag)) return false
                    return log.uid.endsWith(tag)
                }
            }

            Log.installOrThrow(closeChecker)
            try {
                log.installAndTest(deallocateDispatcherDelay = Duration.ZERO) {
                    val logger = Log.Logger.of("CloseChecker")
                    logger.i("Testing1...")
                    assertTrue(log.uninstallAndAwaitAsync())

                    // Should re-acquire CloseableCoroutineDispatcher reference, cancelling
                    // its scheduled deallocation, and re-use it.
                    Log.installOrThrow(log)
                    logger.w("Testing2...")
                }

                // Wait for lazy closure of allocated CloseableCoroutineDispatcher
                // to be reported via debug log.
                withContext(Dispatchers.IO) { delay(750.milliseconds) }
            } finally {
                Log.uninstall(closeChecker)
            }

            println("ALLOCATED  $allocated")
            println("DEALLOCATED$deallocated")
            println("OPENED$opened")
            println("CLOSED$closed")

            assertNotEquals(0, allocated.size)
            assertNotEquals(0, opened.size)
            assertEquals(allocated.size, deallocated.size)
            assertEquals(opened.size, closed.size)

            allocated.forEach { resource ->
                assertTrue(deallocated.contains(resource))
            }
            opened.forEach { closeable ->
                assertTrue(closed.contains(closeable))
            }

            // 2 installs, 1 allocation/de-allocation (i.e. CloseableCoroutineDispatcher
            // closure was canceled, and it was reused by second Log.install of FileLog).
            assertEquals(1, allocated.size)
        }
    }

    /*
    * This test is simply to check that when all Dispatchers.IO threads are locked
    * up (are blocking), FileLog's allocated CloseableCoroutineDispatcher remains
    * unaffected by Thread starvation and works through its load.
    * */
    @Test
    fun givenBlockingConfiguration_whenHeavyLoad_thenWorksThroughAllLogs() = runTest {
        withTmpFile { tmp ->
            val log = FileLog.Builder(tmp.path)
                .maxLogFiles(nFiles = 5)
                .maxLogFileSize(nBytes = 50 * 1024)
                .yieldOn(nLogs = 10)
                .debug(enable = true)
                .build()

            val printer = object : AbstractTestLog("FileLog.Print") {

                override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
                    if (msg == null) return false
                    val print = when {
//                        msg.startsWith("Block[") -> true
                        msg.startsWith("LogRotation") -> true
                        msg.contains("log(s)") -> true
                        else -> false
                    }
                    return if (print) super.log(level, domain, tag, msg, t) else false
                }
            }

            Log.install(printer)
            try {
                log.installAndTest {
                    val logger = Log.Logger.of(tag = "InsaneLoad")

                    val size = 1024
                    val sb = StringBuilder(size)
                    while (sb.length < size) {
                        repeat(127) { sb.append('a') }
                        sb.appendLine()
                    }
                    val s = sb.toString()

                    // Should be enough to lock up ALL Dispatchers.IO threads
                    // while logs are processed.
                    Array(250) {
                        async(Dispatchers.IO) {
                            assertNotEquals(0, logger.w(s))
                        }
                    }.toList().awaitAll()

                    // installAndTest deletes all files, so must check before lambda closure
                    assertTrue(log.uninstallAndAwaitAsync())

                    // log file, including all rotation files should exist
                    log.files.forEach { file ->
                        assertTrue(file.readUtf8().contains(logger.tag), file.toString())
                    }
                }
            } finally {
                Log.uninstall(printer)
            }

            assertEquals(0, log.pendingLogCount)
        }
    }
}
