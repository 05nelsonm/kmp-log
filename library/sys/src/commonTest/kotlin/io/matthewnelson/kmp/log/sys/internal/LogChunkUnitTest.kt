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
package io.matthewnelson.kmp.log.sys.internal

import io.matthewnelson.kmp.log.sys.SysLog
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogChunkUnitTest {

    private companion object {
        // Contains no whitespace characters
        private const val CHARS: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_+-/"

        private const val SP: Char = ' '
    }

    @Test
    fun givenLogChunk_whenMaxLogLenLessThan1_thenThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            SysLog.commonLogChunk("asdfadf", -1) { true }
        }
    }

    @Test
    fun givenLogChunk_whenFormattedIsLessThanMaxLenLog_thenDoesNotChunk() {
        val expected = "expected"
        var i = 0
        val result = SysLog.commonLogChunk(expected, 50) { chunk ->
            assertEquals(expected, chunk)
            i++
            true
        }
        assertTrue(result)
        assertEquals(1, i)
    }

    @Test
    fun givenLogChunk_whenFormattedEndsWithWhitespace_thenIsTrimmed() {
        val expected = "expected"
        val formatted = StringBuilder(expected).appendLine("    ")
        var i = 0
        val result = SysLog.commonLogChunk(formatted, 50) { chunk ->
            assertEquals(expected, chunk)
            i++
            true
        }
        assertTrue(result)
        assertEquals(1, i)
    }

    @Test
    fun givenLogChunk_whenPrintReturnsFalse_thenPopsOutEarly() {
        val expected = "123456"
        val actual = StringBuilder()
        var i = 0
        val result = SysLog.commonLogChunk(expected + "78910", 3) { chunk ->
            assertChunk(chunk, maxLen = 3)
            actual.append(chunk)
            i++
            i < 2
        }
        assertFalse(result)
        assertEquals(2, i)
        assertEquals(expected, actual.toString())
    }

    @Test
    fun givenText_whenContainsNoWhitespace_thenChunksOfMaxLenAreProduced() {
        val expected = randomText(size = 25)
        val actual = StringBuilder()
        var i = 0
        val result = SysLog.commonLogChunk(expected, maxLenLog = 10) { chunk ->
            assertChunk(chunk, maxLen = 10)
            actual.append(chunk)
            i++
            true
        }
        assertTrue(result)
        assertEquals(3, i)
        assertEquals(expected.toString(), actual.toString())
    }

    @Test
    fun givenText_whenContainsWhitespaceNoLineBreaks_thenChunkBreakpointsAreWhitespace() {
        val formatted = randomText(size = 50)
        formatted[17] = SP
        formatted[36] = SP
        // Spaces should be used as breakpoints when chunking, which should not
        // be included in the actual.
        val expected = formatted.toString().replace("$SP", "")
        val actual = StringBuilder()
        var i = 0
        val result = SysLog.commonLogChunk(formatted, maxLenLog = 12) { chunk ->
            assertChunk(chunk, maxLen = 12)
            actual.append(chunk)
            i++
            true
        }
        assertTrue(result)
        assertEquals(6, i)
        assertEquals(expected, actual.toString())
    }

    @Test
    fun givenText_whenBreakpointPrecededByWhitespace_thenWhitespaceIsTrimmed() {
        val formatted = randomText(size = 20)
        formatted[6] = SP
        formatted[7] = CR
        formatted[8] = LF
        val expected = formatted
            .toString()
            .replace("$SP", "")
            .replace("$CR", "")
            .replace("$LF", "")
        val chunks = mutableListOf<String>()
        var i = 0
        val result = SysLog.commonLogChunk(formatted, maxLenLog = 10) { chunk ->
            assertChunk(chunk, maxLen = 10)
            chunks.add(chunk)
            i++
            true
        }
        assertTrue(result)
        assertEquals(3, i)
        assertEquals(formatted.substring(0, 6), chunks.first())
        assertEquals(expected, chunks.joinToString(separator = ""))
    }

    @Test
    fun givenText_whenFinalChunkIsLessThanMaxLen_thenIsNotSplitByAnyWhitespace() {
        val expected = randomText(size = 19)
        expected[12] = SP
        expected[13] = CR
        expected[14] = LF
        val chunks = mutableListOf<String>()
        var i = 0
        val result = SysLog.commonLogChunk(expected, maxLenLog = 10) { chunk ->
            assertChunk(chunk, maxLen = 10)
            chunks.add(chunk)
            i++
            true
        }
        assertTrue(result)
        assertEquals(2, i)
        assertEquals(expected.substring(0, 10), chunks.first())
        assertEquals(expected.substring(10, 19), chunks.last())
        assertEquals(expected.toString(), chunks.joinToString(separator = ""))
    }

    private fun assertChunk(chunk: String, maxLen: Int) {
        assertTrue(chunk.isNotEmpty(), "chunk.isNotEmpty() == false")
        assertTrue(chunk.length <= maxLen, "chunk.length > maxLen")
    }

    private fun randomText(size: Int, chars: String = CHARS): StringBuilder {
        val sb = StringBuilder(size)
        repeat(size) {
            val i = Random.nextInt(from = 0, until = chars.length)
            sb.append(chars[i])
        }
        return sb
    }
}
