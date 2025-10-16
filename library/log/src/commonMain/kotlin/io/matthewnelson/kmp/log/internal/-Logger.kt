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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.internal

import io.matthewnelson.kmp.log.Log

private const val MAX_LEN_DOMAIN: Int = 32
private const val MIN_LEN_DOMAIN: Int = 3
private const val MAX_LEN_TAG: Int = MAX_LEN_DOMAIN * 4 // 128
private const val SEPARATORS: String = ".-:"

@Throws(IllegalArgumentException::class, NullPointerException::class)
internal inline fun Log.Logger.Companion.commonCheckTag(tag: String?): String {
    if (tag == null) throw NullPointerException("tag == null")
    require(tag.isNotEmpty()) { "tag cannot be empty" }
    require(tag.length <= MAX_LEN_TAG) { "tag.length[${tag.length}] > max[${MAX_LEN_TAG}]" }
    require(tag.indexOfFirst { it.isWhitespace() } == -1) { "tag cannot contain whitespace" }
    return tag
}

@Throws(IllegalArgumentException::class)
internal inline fun Log.Logger.Companion.commonCheckDomain(domain: String?): String? {
    if (domain == null) return domain
    require(domain.length >= MIN_LEN_DOMAIN) { "domain.length[${domain.length}] < min[${MIN_LEN_DOMAIN}]" }
    require(domain.length <= MAX_LEN_DOMAIN) { "domain.length[${domain.length}] > max[${MAX_LEN_DOMAIN}]" }

    var separatorChars = 0
    var lastCharWasSeparator = false
    domain.forEachIndexed { i, c ->
        when (c) {
            in '0'..'9' -> lastCharWasSeparator = false
            in 'a'..'z' -> lastCharWasSeparator = false
            else -> if (SEPARATORS.contains(c)) {
                separatorChars++
                if (lastCharWasSeparator) {
                    var msg = "Invalid domain character[$c] at index[$i]. domain separator characters"
                    msg += SEPARATORS.toList().toString()
                    msg += " cannot precede or follow another separator character."
                    throw IllegalArgumentException(msg)
                }
                if (i == 0) {
                    throw IllegalArgumentException("domain must start with character [a-z] or [0-9]")
                }
                if (i == domain.lastIndex) {
                    throw IllegalArgumentException("domain must end with character [a-z] or [0-9]")
                }
                lastCharWasSeparator = true
            } else {
                var msg = "Invalid domain character[$c] at index[$i]."
                msg += " Allowable characters are [a-z][0-9][$SEPARATORS]"
                throw IllegalArgumentException(msg)
            }
        }
    }
    require(separatorChars > 0) { "domain must contain at least 1 separator character${SEPARATORS.toList()}" }
    return domain
}
