package prog8tests.helpers

import prog8.ast.IBuiltinFunctions
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteral
import prog8.code.core.*


internal object DummyFunctions : IBuiltinFunctions {
    override val names: Set<String> = emptySet()
    override val purefunctionNames: Set<String> = emptySet()
    override fun constValue(
        name: String,
        args: List<Expression>,
        position: Position,
    ): NumericLiteral? = null

    override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
}

internal object DummyMemsizer : IMemSizer {
    override fun memorySize(dt: DataType) = 0
}

internal object DummyStringEncoder : IStringEncoding {
    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        return emptyList()
    }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        return ""
    }
}

internal object AsciiStringEncoder : IStringEncoding {
    override fun encodeString(str: String, encoding: Encoding): List<UByte> = str.map { it.code.toUByte() }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        return bytes.joinToString()
    }
}

internal object DummyCompilationTarget : ICompilationTarget {
    override val name: String = "dummy"
    override val machine: IMachineDefinition
        get() = throw NotImplementedError("dummy")
    override val supportedEncodings = setOf(Encoding.PETSCII, Encoding.SCREENCODES, Encoding.ISO)
    override val defaultEncoding = Encoding.PETSCII

    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        throw NotImplementedError("dummy")
    }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        throw NotImplementedError("dummy")
    }

    override fun memorySize(dt: DataType): Int {
        throw NotImplementedError("dummy")
    }
}
