package prog8.compilerinterface

import prog8.compiler.IMemSizer

interface ICompilationTarget: IStringEncoding, IMemSizer {
    val name: String
    val machine: IMachineDefinition
    override fun encodeString(str: String, altEncoding: Boolean): List<Short>
    override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String
}
