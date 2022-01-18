package prog8tests.helpers

import prog8.ast.IBuiltinFunctions
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.compilerinterface.*

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
    override fun memorySize(dt: DataType) = 0
}

internal val DummyStringEncoder = object : IStringEncoding {
    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        return emptyList()
    }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        return ""
    }
}

internal val AsciiStringEncoder = object : IStringEncoding {
    override fun encodeString(str: String, encoding: Encoding): List<UByte> = str.map { it.code.toUByte() }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        return bytes.joinToString()
    }
}

internal val DummyCompilationTarget = object : ICompilationTarget {
    override val name: String = "dummy"
    override val machine: IMachineDefinition
        get() = throw NotImplementedError("dummy")

    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        throw NotImplementedError("dummy")
    }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        throw NotImplementedError("dummy")
    }

    override fun asmsubArgsEvalOrder(sub: Subroutine): List<Int> {
        throw NotImplementedError("dummy")
    }

    override fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>,
                                                   paramRegisters: List<RegisterOrStatusflag>): Boolean {
        throw NotImplementedError("dummy")
    }

    override fun memorySize(dt: DataType): Int {
        throw NotImplementedError("dummy")
    }
}