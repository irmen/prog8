package prog8.codegen.target

import com.github.michaelbull.result.fold
import prog8.ast.base.FatalAstException
import prog8.codegen.target.cbm.IsoEncoding
import prog8.codegen.target.cbm.PetsciiEncoding
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.IStringEncoding

internal object Encoder: IStringEncoding {
    override fun encodeString(str: String, encoding: Encoding): List<UByte> {              // TODO use Result
        val coded = when(encoding) {
            Encoding.PETSCII -> PetsciiEncoding.encodePetscii(str, true)
            Encoding.SCREENCODES -> PetsciiEncoding.encodeScreencode(str, true)
            Encoding.ISO -> IsoEncoding.encode(str)
            else -> throw FatalAstException("unsupported encoding $encoding")
        }
        return coded.fold(
            failure = { throw it },
            success = { it }
        )
    }
    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {            // TODO use Result
        val decoded = when(encoding) {
            Encoding.PETSCII -> PetsciiEncoding.decodePetscii(bytes, true)
            Encoding.SCREENCODES -> PetsciiEncoding.decodeScreencode(bytes, true)
            Encoding.ISO -> IsoEncoding.decode(bytes)
            else -> throw FatalAstException("unsupported encoding $encoding")
        }
        return decoded.fold(
            failure = { throw it },
            success = { it }
        )
    }
}