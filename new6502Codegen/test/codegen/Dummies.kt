package codegen

import prog8.code.core.Encoding
import prog8.code.core.IStringEncoding

object DummyStringEncoder : IStringEncoding {
    override val defaultEncoding: Encoding = Encoding.ISO
    override fun encodeString(str: String, encoding: Encoding): List<UByte> = emptyList()
    override fun decodeString(bytes: Iterable<UByte>, encoding: Encoding): String = ""
}
