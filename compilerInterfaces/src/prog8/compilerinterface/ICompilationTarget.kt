package prog8.compilerinterface

interface ICompilationTarget: IStringEncoding, IMemSizer {
    val name: String
    val machine: IMachineDefinition
    override fun encodeString(str: String, altEncoding: Boolean): List<UByte>
    override fun decodeString(bytes: List<UByte>, altEncoding: Boolean): String
}
