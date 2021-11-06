package prog8tests.ast.helpers

import prog8.ast.IBuiltinFunctions
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteralValue
import prog8.compilerinterface.IMemSizer
import prog8.compilerinterface.IStringEncoding

internal val DummyFunctions = object : IBuiltinFunctions {
    override val names: Set<String> = emptySet()
    override val purefunctionNames: Set<String> = emptySet()
    override fun constValue(
        name: String,
        args: List<Expression>,
        position: Position,
    ): NumericLiteralValue? = null

    override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
}

internal val DummyMemsizer = object : IMemSizer {
    override fun memorySize(dt: DataType): Int = 0
}

internal val DummyStringEncoder = object : IStringEncoding {
    override fun encodeString(str: String, altEncoding: Boolean): List<Short> {
        return emptyList()
    }

    override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String {
        return ""
    }
}

internal val AsciiStringEncoder = object : IStringEncoding {
    override fun encodeString(str: String, altEncoding: Boolean): List<Short> = str.map { it.code.toShort() }

    override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String {
        return bytes.joinToString()
    }
}