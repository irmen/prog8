package prog8.code.target.encodings

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.io.CharConversionException
import java.nio.charset.Charset

object KatakanaEncoding {
    val charset: Charset = Charset.forName("JIS_X0201")

    fun encode(str: String): Result<List<UByte>, CharConversionException> {
        return try {
            val mapped = str.map { chr ->
                when (chr) {
                    // TODO: Convert regular katakana to halfwidth katakana (java lib doesn't do that for us
                    //       and simply returns '?' upon reaching a regular katakana character)
                    //       NOTE: we probably need to somehow do that before we reach this `when`,
                    //             as one regular katakana character often results in two HW katakana characters
                    //             due to differences in how diacritics are handled.

                    '\u0000' -> 0u
                    '\u00a0' -> 0xa0u // $a0 isn't technically a part of JIS X 0201 spec, and so we need to handle this ourselves

                    '♥' -> 0xe3u
                    '♦' -> 0xe4u
                    '♣' -> 0xe5u
                    '♠' -> 0xe6u

                    '大' -> 0xeau
                    '中' -> 0xebu
                    '小' -> 0xecu
                    '百' -> 0xedu
                    '千' -> 0xeeu
                    '万' -> 0xefu
                    '♪' -> 0xf0u
                    '土' -> 0xf1u
                    '金' -> 0xf2u
                    '木' -> 0xf3u
                    '水' -> 0xf4u
                    '火' -> 0xf5u
                    '月' -> 0xf6u
                    '日' -> 0xf7u
                    '時' -> 0xf8u
                    '分' -> 0xf9u
                    '秒' -> 0xfau
                    '年' -> 0xfbu
                    '円' -> 0xfcu
                    '人' -> 0xfdu
                    '生' -> 0xfeu
                    '〒' -> 0xffu
                    in '\u8000'..'\u80ff' -> {
                        // special case: take the lower 8 bit hex value directly
                        (chr.code - 0x8000).toUByte()
                    }
                    else -> charset.encode(chr.toString())[0].toUByte()
                }
            }
            Ok(mapped)
        } catch (ce: CharConversionException) {
            Err(ce)
        }
    }

    fun decode(bytes: Iterable<UByte>): Result<String, CharConversionException> {
        return try {
            Ok(String(bytes.map { it.toByte() }.toByteArray(), charset))
        } catch (ce: CharConversionException) {
            Err(ce)
        }
    }
}