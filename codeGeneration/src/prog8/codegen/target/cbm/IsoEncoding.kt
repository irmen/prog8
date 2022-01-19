package prog8.codegen.target.cbm

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Err
import java.io.CharConversionException

object IsoEncoding {
    fun encode(str: String): Result<List<UByte>, CharConversionException> {
        return try {
            Ok(str.toByteArray(Charsets.ISO_8859_1).map { it.toUByte() })
        } catch (ce: CharConversionException) {
            Err(ce)
        }
    }

    fun decode(bytes: List<UByte>): String {
        // TODO use Result
        return String(bytes.map { it.toByte() }.toByteArray(), Charsets.ISO_8859_1)
    }
}
