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

import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.sys.SysLog
import io.matthewnelson.kmp.log.sys.deviceApiLevel
import io.matthewnelson.kmp.log.sys.isNative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AndroidTagTest {

    @Test
    fun givenTag_whenAndroidApi25OrBelow_thenIsTruncatedIfTooLong() {
        var tag = if (isNative()) "Native-" else "NonNative-"
        while (tag.length < 30) { tag += "A" }
        val sdkInt = deviceApiLevel()
        val actual = SysLog.androidDomainTag(sdkInt, domain = null, tag)
        if (sdkInt <= 25) {
            assertEquals(23, actual.length)
            assertNotEquals(tag, actual)
        } else {
            assertEquals(tag, actual)
        }

        Log.installOrThrow(SysLog.Debug)
        try {
            val result = Log.Logger.of(tag = tag).i {
                "Test tag of length[${tag.length}] works on all API levels and will not cause an exception"
            }
            assertEquals(1, result)
        } finally {
            Log.uninstallOrThrow(SysLog.UID)
        }
    }
}
