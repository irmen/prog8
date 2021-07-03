package prog8.parser

import prog8.ast.IStringEncoding
import java.io.CharConversionException


/**
 * TODO: remove once [IStringEncoding] has been to compiler module
 */
object PetsciiEncoding : IStringEncoding {
    override fun encodeString(str: String, altEncoding: Boolean) =
        try {
            if (altEncoding) Petscii.encodeScreencode(str, true) else Petscii.encodePetscii(str, true)
        } catch (x: CharConversionException) {
            throw CharConversionException("can't convert string to target machine's char encoding: ${x.message}")
        }

    override fun decodeString(bytes: List<Short>, altEncoding: Boolean) =
        try {
            if (altEncoding) Petscii.decodeScreencode(bytes, true) else Petscii.decodePetscii(bytes, true)
        } catch (x: CharConversionException) {
            throw CharConversionException("can't decode string: ${x.message}")
        }
}
