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
package io.matthewnelson.kmp.log.sys

import android.os.Build
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.readUtf8
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.file.FileLog
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidFileLogTest {

    @Test
    fun givenAndroid_whenFileLog_thenOperatesAsExpected() {
        val tmp = SysTempDir.resolve("kmp_file_log-" + Random.nextBytes(ByteArray(16)).encodeToString(Base16))
        val log = FileLog.Builder(tmp.path)
            .min(Log.Level.Verbose)
            .minWaitOn(Log.Level.Debug)
            .bufferCapacity(500)
            .bufferOverflow(dropOldest = true)
            .maxLogFiles(nFiles = 5)
            .maxLogFileSize(nBytes = 0)
            .yieldOn(nLogs = 10)
            .whitelistTag("AndroidFileLogTest")
            .debug(enable = true)
            .build()

        val opened = mutableListOf<String>()
        val closed = mutableListOf<String>()

        // If changing uid, update build.gradle.kts
        val checker = object : Log(uid = "AndroidFileLogChecker", Level.Debug) {

            private val LOG = Logger.of(uid)

            override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
                if (msg != null) when {
                    msg.startsWith("Opened >> ") -> synchronized(this) {
                        opened.add(msg.substringAfter("Opened >> "))
                    }
                    msg.startsWith("Closed >> ") -> synchronized(this) {
                        closed.add(msg.substringAfter("Closed >> "))
                    }
                }

                return LOG.log(level, t, msg) > 0
            }

            override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
                return domain == FileLog.DOMAIN && log.uid.endsWith(tag)
            }
        }

        Log.installOrThrow(SysLog.Debug)
        Log.installOrThrow(checker)
        Log.installOrThrow(log)
        try {
            val s = buildString(1024) {
                while (length < 1024) {
                    repeat(128) { append('a') }
                    appendLine()
                }
            }
            val logger = Log.Logger.of(log.whitelistTag.first())

            // Level.Verbose so that SysLog does not pick anything up
            repeat(125) { logger.v(s) }

            log.uninstallAndAwaitSync()

            // Check to ensure implementation's pid/tid logic for android is OK.
            val line = log.logFiles[0].toFile()
                .readUtf8()
                .lines()
                .first()

            val actualPID = line
                .substringAfter("V ")
                .substringBefore(" ")
                .toInt()
            val actualTID = line
                .substringAfter("V ")
                .substringAfter("$actualPID ")
                .substringBefore(" ")
                .toLong()

            val expectedPID = android.os.Process.myPid()
            val expectedTID = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                Thread.currentThread().threadId()
            } else {
                @Suppress("DEPRECATION")
                Thread.currentThread().id
            }

            assertEquals(expectedPID, actualPID, "pid")
            assertEquals(expectedTID, actualTID, "tid")
        } finally {
            log.uninstallAndAwaitSync()
            tmp.deleteRecursively()
            Log.uninstallAll(evenAbortHandler = false)
        }

        println("OPENED$opened")
        println("CLOSED$closed")

        assertNotEquals(0, opened.size)
        assertEquals(opened.size, closed.size)

        opened.forEach { open ->
            assertTrue(closed.contains(open), "Not closed >> $open")
        }

        // Check that reflection based openDirectory implementation
        // which utilizes ParcelFileDescriptor was used.
        assertNotNull(
            opened.firstOrNull { it.startsWith("AndroidDirectory") },
            "opened did not contain an AndroidDirectory",
        )
    }
}
