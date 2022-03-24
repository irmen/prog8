package prog8.code.target.cbm

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.io.CharConversionException
import java.nio.charset.Charset

object IsoEncoding {
    val charset: Charset = Charset.forName("ISO-8859-15")

    fun encode(str: String): Result<List<UByte>, CharConversionException> {
        return try {
            val mapped = str.map { chr ->
                when (chr) {
                    '\u0000' -> 0u
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

    fun decode(bytes: List<UByte>): Result<String, CharConversionException> {
        return try {
            Ok(String(bytes.map { it.toByte() }.toByteArray(), charset))
        } catch (ce: CharConversionException) {
            Err(ce)
        }
    }
}
