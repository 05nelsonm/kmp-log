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
package io.matthewnelson.kmp.log.test.file.lock

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal fun main(array: Array<out String>) {
    require(array.size == 4) { "array.size != 4 >> e.g. [/path/to/file, 500, 0, 1]" }
    val file = File(array[0])
    require(file.isAbsolute) { "array[0].isAbsolute == false" }
    require(file.exists()) { "array[0].exists() == false" }

    val timeout = array[1].toInt().milliseconds
    require(timeout in 150.milliseconds..2_000.milliseconds) { "array[1] !in 150..2_000" }

    val position = array[2].toLong()
    val size = array[3].toLong()
    require(position >= 0L) { "position < 0L" }
    require(size >= 0L) { "size < 0L" }
    require(position + size >= 0L) { "position + size < 0L" }

    RandomAccessFile(file, "rw").channel.use { ch ->
        val mark = TimeSource.Monotonic.markNow()
        var lock: FileLock?
        while (true) {
            lock = ch.tryLock(position, size, false)
            if (lock != null) break
            Thread.sleep(50)
            if (mark.elapsedNow() > timeout) break
        }
        if (lock != null) {
            println("ACQUIRED[$position, $size]")
            val remainder = (timeout - mark.elapsedNow()).inWholeMilliseconds.coerceAtLeast(1L)
            Thread.sleep(remainder)
            println("RELEASING[$position, $size]")
            lock.release()
            Thread.sleep(100L)
            println("RELEASED[$position, $size]")
        } else {
            println("FAILURE[$position, $size]")
        }
    }
}
