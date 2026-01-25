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
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.Log.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
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

            val closeChecker = object : Log(uid = "CloseChecker", min = Level.Debug) {
                override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
                    if (msg == null) return false
                    if (msg.startsWith("Allocated >> ")) {
                        allocated.add(msg.substringAfter("Allocated >> "))
                        return true
                    }
                    if (msg.startsWith("Deallocated >> ")) {
                        deallocated.add(msg.substringAfter("Deallocated >> "))
                        return true
                    }
                    if (msg.startsWith("Opened >> ")) {
                        opened.add(msg.substringAfter("Opened >> "))
                        return true
                    }
                    if (msg.startsWith("Closed >> ")) {
                        closed.add(msg.substringAfter("Closed >> "))
                        return true
                    }
                    return false
                }
                override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
                    if (domain != FileLog.DOMAIN) return false
                    return log.uid.endsWith(tag)
                }
            }

            Log.installOrThrow(closeChecker)
            try {
                log.installAndTest {
                    val LOG = Log.Logger.of("CloseChecker")
                    LOG.i("Testing1...")
                    // Should re-acquire CoroutineDispatcher reference and
                    // not deallocate it, but re-use it.
                    Log.uninstallOrThrow(log)
                    Log.installOrThrow(log)
                    withContext(Dispatchers.IO) { delay(25.milliseconds) }
                    LOG.w("Testing2...")
                }
            } finally {
                // Wait for lazy closure of allocated CloseableCoroutineDispatcher
                withContext(Dispatchers.IO) { delay(500.milliseconds) }
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

            // 2 installs, 1 allocation/de-allocation (close was canceled and it was reused)
            assertEquals(1, allocated.size)
        }
    }
}
