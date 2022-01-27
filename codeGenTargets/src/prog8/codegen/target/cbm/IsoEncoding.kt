package prog8.codegen.target.cbm

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Err
import java.io.CharConversionException
import java.nio.charset.Charset

object IsoEncoding {
    val charset: Charset = Charset.forName("ISO-8859-15")

    fun encode(str: String): Result<List<UByte>, CharConversionException> {
        return try {
            Ok(str.toByteArray(charset).map { it.toUByte() })
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
