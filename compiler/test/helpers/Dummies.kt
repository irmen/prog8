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
        funcName: String,
        args: List<Expression>,
        position: Position,
    ): NumericLiteral? = null

    override fun returnType(funcName: String) = InferredTypes.unknown()
}

internal object DummyMemsizer : IMemSizer {
    override fun memorySize(dt: DataType, numElements: Int?): Int {
        if(dt.isPointerArray)
            return 2 * numElements!!
        else if(dt.isArray || dt.isSplitWordArray) {
            require(numElements!=null)
            return when(dt.sub) {
                BaseDataType.BOOL, BaseDataType.BYTE, BaseDataType.UBYTE -> numElements
                BaseDataType.UWORD, BaseDataType.WORD -> numElements*2
                BaseDataType.LONG -> numElements*4
                BaseDataType.FLOAT -> numElements*5
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        return when {
            dt.isByteOrBool -> 1 * (numElements ?: 1)
            dt.isLong -> 4 * (numElements ?: 1)
            dt.isFloat -> 5 * (numElements ?: 1)
            else -> 2 * (numElements ?: 1)
        }
    }

    override fun memorySize(dt: BaseDataType): Int {
        return memorySize(DataType.forDt(dt), null)
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
