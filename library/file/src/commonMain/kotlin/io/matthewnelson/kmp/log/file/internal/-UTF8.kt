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
@file:Suppress("NOTHING_TO_INLINE", "RemoveRedundantQualifierName", "ConvertTwoComparisonsToRangeCheck")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.encoding.core.Decoder
import io.matthewnelson.encoding.core.Encoder
import io.matthewnelson.encoding.core.EncoderDecoder
import io.matthewnelson.encoding.core.EncodingException
import io.matthewnelson.encoding.core.util.DecoderInput
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmStatic

// Jvm uses ?.code.toByte() as a replacement for invalid UTF-8
// sequences, whereas all other platforms uses the byte sequence
// 0xef 0xbf 0xbd
internal expect val UTF8.INVALID_UTF8_SEQUENCE_SIZE: Int
internal expect inline fun Decoder.OutFeed.outputInvalidUTF8Sequence()

private val CONFIG = object : EncoderDecoder.Config(null, 0, null) {

    // This is actually encoding, not decoding (String/CharArray -> ByteArray)
    override fun decodeOutMaxSizeOrFailProtected(
        encodedSize: Int,
        input: DecoderInput,
    ): Int = UTF8.utf8Size(limit = encodedSize, _get = input::get).toInt()

    // This is actually encoding, not decoding (String/CharArray -> ByteArray)
    override fun decodeOutMaxSizeProtected(
        encodedSize: Long,
    ): Long = encodedSize * 3

    // This is actually decoding, not encoding (ByteArray -> String/CharArray)
    override fun encodeOutSizeProtected(
        unEncodedSize: Long,
    ): Long = throw EncodingException("UTF-8 decoding is not supported")

    override fun toStringAddSettings(): Set<Setting> = emptySet()
}

internal object UTF8: EncoderDecoder<EncoderDecoder.Config>(CONFIG) {

    @JvmStatic
    internal fun CharSequence.utf8Size(): Long = utf8Size(limit = length, _get = ::get)

    private const val NAME = "UTF-8"

    override fun name(): String = NAME

    // This is actually encoding, not decoding (String/CharArray -> ByteArray)
    override fun newDecoderFeedProtected(out: Decoder.OutFeed): Decoder<Config>.Feed = object : Decoder<Config>.Feed() {

        private var cBuf: Int? = null

        override fun consumeProtected(input: Char) {
            val c = cBuf ?: run {
                cBuf = input.code
                return
            }
            val cNext = input.code

            if (c < 0x0080) {
                cBuf = cNext
                out.output(c.toByte())
                return
            }
            if (c < 0x0800) {
                cBuf = cNext
                out.output((c  shr  6          or 0xc0).toByte())
                out.output((c         and 0x3f or 0x80).toByte())
                return
            }
            if (c < 0xd800 || c > 0xdfff) {
                cBuf = cNext
                out.output((c  shr 12          or 0xe0).toByte())
                out.output((c  shr  6 and 0x3f or 0x80).toByte())
                out.output((c         and 0x3f or 0x80).toByte())
                return
            }
            if (c > 0xdbff) {
                cBuf = cNext
                out.outputInvalidUTF8Sequence()
                return
            }

            if (cNext < 0xdc00 || cNext > 0xdfff) {
                cBuf = cNext
                out.outputInvalidUTF8Sequence()
                return
            }

            cBuf = null
            val cp = ((c shl 10) + cNext) + (0x010000 - (0xd800 shl 10) - 0xdc00)
            out.output((cp shr 18          or 0xf0).toByte())
            out.output((cp shr 12 and 0x3f or 0x80).toByte())
            out.output((cp shr  6 and 0x3f or 0x80).toByte())
            out.output((cp        and 0x3f or 0x80).toByte())
        }

        override fun doFinalProtected() {
            val c = cBuf ?: return
            cBuf = null
            if (c < 0x0080) {
                out.output(c.toByte())
                return
            }
            if (c < 0x0800) {
                out.output((c  shr  6          or 0xc0).toByte())
                out.output((c         and 0x3f or 0x80).toByte())
                return
            }
            if (c < 0xd800 || c > 0xdfff) {
                out.output((c  shr 12          or 0xe0).toByte())
                out.output((c  shr  6 and 0x3f or 0x80).toByte())
                out.output((c         and 0x3f or 0x80).toByte())
                return
            }
//            if (c > 0xdbff) {
//                out.outputInvalidUTF8Sequence()
//                return
//            }

            out.outputInvalidUTF8Sequence()
        }
    }

    // This is actually decoding, not encoding (ByteArray -> String/CharArray)
    override fun newEncoderFeedProtected(out: Encoder.OutFeed): Encoder<Config>.Feed {
        throw EncodingException("UTF-8 decoding is not supported")
    }
}


@Suppress("LocalVariableName")
@OptIn(ExperimentalContracts::class)
private inline fun UTF8.utf8Size(
    limit: Int,
    _get: (Int) -> Char,
): Long {
    contract { callsInPlace(_get, InvocationKind.UNKNOWN) }

    var size = 0L
    var cPos = 0
    while (cPos < limit) {
        val c = _get(cPos++).code
        if (c < 0x0080) {
            size += 1
            continue
        }
        if (c < 0x0800) {
            size += 2
            continue
        }
        if (c < 0xd800 || c > 0xdfff) {
            size += 3
            continue
        }
        if (c > 0xdbff) {
            size += INVALID_UTF8_SEQUENCE_SIZE
            continue
        }
        if (cPos >= limit) {
            size += INVALID_UTF8_SEQUENCE_SIZE
            continue
        }

        val cNext = _get(cPos).code
        if (cNext < 0xdc00 || cNext > 0xdfff) {
            size += INVALID_UTF8_SEQUENCE_SIZE
            continue
        }

        size += 4
        cPos++
    }
    return size
}
