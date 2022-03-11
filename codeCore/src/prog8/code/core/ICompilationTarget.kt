package prog8.code.core

interface ICompilationTarget: IStringEncoding, IMemSizer {
    val name: String
    val machine: IMachineDefinition
    val supportedEncodings: Set<Encoding>
    val defaultEncoding: Encoding

    override fun encodeString(str: String, encoding: Encoding): List<UByte>
    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String
}
