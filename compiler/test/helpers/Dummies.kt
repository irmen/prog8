package prog8tests.helpers

import prog8.ast.IBuiltinFunctions
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteral
import prog8.code.core.*
import prog8.code.target.virtual.VirtualMachineDefinition


internal object DummyFunctions : IBuiltinFunctions {
    override val names: Set<String> = emptySet()
    override val purefunctionNames: Set<String> = emptySet()
    override fun constValue(
        funcName: String,
        args: List<Expression>,
        position: Position,
    ): NumericLiteral? = null

    override fun returnType(funcName: String) = InferredTypes.InferredType.unknown()
}

internal object DummyMemsizer : IMemSizer {
    override fun memorySize(dt: DataType) = when(dt) {
        in ByteDatatypes, DataType.BOOL -> 1
        DataType.FLOAT -> 5
        else -> 2
    }
    override fun memorySize(arrayDt: DataType, numElements: Int) = when(arrayDt) {
        DataType.ARRAY_UW -> numElements*2
        DataType.ARRAY_W -> numElements*2
        DataType.ARRAY_F -> numElements*5
        else -> numElements
    }
}

internal object DummyStringEncoder : IStringEncoding {
    override val defaultEncoding: Encoding = Encoding.ISO

    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        return emptyList()
    }

    override fun decodeString(bytes: Iterable<UByte>, encoding: Encoding): String {
        return ""
    }
}

internal object AsciiStringEncoder : IStringEncoding {
    override val defaultEncoding: Encoding = Encoding.ISO

    override fun encodeString(str: String, encoding: Encoding): List<UByte> = str.map { it.code.toUByte() }

    override fun decodeString(bytes: Iterable<UByte>, encoding: Encoding): String {
        return bytes.joinToString()
    }
}

internal object DummyCompilationTarget : ICompilationTarget {
    override val name: String = "dummy"
    override val machine: IMachineDefinition = VirtualMachineDefinition()  // not really true but I don't want to implement a full dummy machinedef
    override val defaultEncoding = Encoding.PETSCII

    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        throw NotImplementedError("dummy")
    }

    override fun decodeString(bytes: Iterable<UByte>, encoding: Encoding): String {
        throw NotImplementedError("dummy")
    }

    override fun memorySize(dt: DataType): Int {
        throw NotImplementedError("dummy")
    }

    override fun memorySize(arrayDt: DataType, numElements: Int): Int {
        throw NotImplementedError("dummy")
    }
}
