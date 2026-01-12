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
package io.matthewnelson.kmp.log.sys

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import java.io.RandomAccessFile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AndroidNativeTest {

    private val ctx = ApplicationProvider.getApplicationContext<Application>().applicationContext
    private val nativeLibraryDir = ctx.applicationInfo.nativeLibraryDir.toFile().absoluteFile

    @Test
    fun givenAndroidNative_whenTestSysBinary_thenIsSuccessful() {
        run("libTestSys.so", 2.minutes) {}
    }

    @Test
    fun givenAndroidNative_whenTestLogBinary_thenIsSuccessful() {
        run("libTestLog.so", 3.minutes) {}
    }

    @Test
    fun givenAndroidNative_whenTestFileBinary_thenIsSuccessful() {
        // See :library:file androidNativeTest source set
        var name = "kmp-log.file.test-"
        name += Random.nextBytes(12).encodeToString(Base16)
        val file = SysTempDir.resolve(name)

        try {
            RandomAccessFile(file, "rw").channel.use { ch ->
                // Do not block. Should just work.
                val lock1 = ch.tryLock(0, 1, false)
                assertNotNull(lock1, "lock1 was null???")
                val lock2 = ch.tryLock(Long.MAX_VALUE - 1, 0, false)
                assertNotNull(lock2, "lock2 was null??")

                run("libTestFile.so", 5.minutes) { env ->
                    env["io_matthewnelson_kmp_log_file_test_setlk_path"] = file.path
                }
            }
        } finally {
            file.delete2()
        }
    }

    private fun run(libName: String, timeout: Duration, env: (MutableMap<String, String>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            println("Skipping...")
            return
        }

        val out = Process.Builder(executable = nativeLibraryDir.resolve(libName))
            .stdin(Stdio.Null)
            .environment(env)
            .createOutput {
                maxBuffer = Int.MAX_VALUE / 2
                timeoutMillis = timeout.inWholeMilliseconds.toInt()
            }

        if (out.processInfo.exitCode == 0) {
            println(out.stdout)
            return
        }

        System.err.println(out.toString())
        System.err.println(out.stdout)
        System.err.println(out.stderr)
        System.err.println("--- ENVIRONMENT ---")
        out.processInfo.environment.forEach { (key, value) -> System.err.println("$key=$value") }
        throw AssertionError("Process.exitCode[${out.processInfo.exitCode}] != 0")
    }
}
