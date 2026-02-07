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

import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.NotDirectoryException
import io.matthewnelson.kmp.file.SysFsInfo
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.use
import io.matthewnelson.kmp.file.writeUtf8
import io.matthewnelson.kmp.log.file.withTmpFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DirectoryUnitTest {

    @Test
    fun givenOpen_whenDoesNotExist_thenThrowsFileNotFoundException() = withTmpFile { tmp ->
        assertFailsWith<FileNotFoundException> { tmp.openDirectory().close() }
    }

    @Test
    fun givenOpen_whenNotDirectory_thenThrowsNotDirectoryException() = withTmpFile { tmp ->
        tmp.writeUtf8(null, "Hello World!")
        assertFailsWith<NotDirectoryException> { tmp.openDirectory().close() }
    }

    @Test
    fun givenOpen_whenDirectory_thenSyncSucceeds() = withTmpFile { tmp ->
        tmp.mkdirs2(mode = null).openDirectory().use { directory ->
            directory.sync()
            when (SysFsInfo.name) {
                "FsJvmNioNonPosix", "FsMinGW" -> assertEquals(Directory.NoOp, directory)
            }
        }
    }
}
