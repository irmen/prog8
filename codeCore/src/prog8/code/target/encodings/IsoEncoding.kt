package prog8.code.target.encodings

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.io.CharConversionException
import java.nio.charset.Charset


open class IsoEncodingBase(charsetName: String) {
    val charset: Charset = Charset.forName(charsetName)

    fun encode(str: String, newlineToCarriageReturn: Boolean): Result<List<UByte>, CharConversionException> {
        return try {
            val mapped = str.map { chr ->
                when (chr) {
                    '\u0000' -> 0u
                    '\n' -> if(newlineToCarriageReturn) 13u else 10u
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

    fun decode(bytes: Iterable<UByte>, newlineToCarriageReturn: Boolean): Result<String, CharConversionException> {
        return try {
            Ok(String(bytes.map {
                when(it) {
                    13u.toUByte() -> if(newlineToCarriageReturn) 10 else 13
                    else -> it.toByte()
                }
            }.toByteArray(), charset))
        } catch (ce: CharConversionException) {
            Err(ce)
        }
    }
}


object IsoEncoding: IsoEncodingBase("ISO-8859-15")
object IsoCyrillicEncoding: IsoEncodingBase("ISO-8859-5")
object IsoEasternEncoding: IsoEncodingBase("ISO-8859-16")
