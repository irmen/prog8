package prog8.code.target.encodings

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.io.CharConversionException
import java.nio.charset.Charset

object Cp437Encoding {
    val charset: Charset = Charset.forName("IBM437")

    fun encode(str: String): Result<List<UByte>, CharConversionException> {
        return try {
            val mapped = str.map { chr ->
                when (chr) {
                    '\u0000' -> 0u
                    '\u00a0' -> 255u
                    '☺' -> 1u
                    '☻' -> 2u
                    '♥' -> 3u
                    '♦' -> 4u
                    '♣' -> 5u
                    '♠' -> 6u
                    '•' -> 7u
                    '◘' -> 8u
                    '○' -> 9u
                    '◙' -> 10u
                    '♂' -> 11u
                    '♀' -> 12u
                    '♪' -> 13u
                    '♫' -> 14u
                    '☼' -> 15u
                    '►' -> 16u
                    '◄' -> 17u
                    '↕' -> 18u
                    '‼' -> 19u
                    '¶' -> 20u
                    '§' -> 21u
                    '▬' -> 22u
                    '↨' -> 23u
                    '↑' -> 24u
                    '↓' -> 25u
                    '→' -> 26u
                    '←' -> 27u
                    '∟' -> 28u
                    '↔' -> 29u
                    '▲' -> 30u
                    '▼' -> 31u
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
