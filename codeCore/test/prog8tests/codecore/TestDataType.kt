package prog8tests.codecore

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.core.*

/**
 * Unit tests for DataType and BaseDataType classes in codeCore.
 * Focus on type predicates, relationships, and key operations.
 */
class TestDataType: FunSpec({

    val dummyMemSizer = object : IMemSizer {
        override fun memorySize(dt: DataType, numElements: Int?): Int {
            if (dt.isPointerArray) return 2 * numElements!!
            if (dt.isArray || dt.isSplitWordArray) {
                require(numElements != null)
                return when (dt.sub) {
                    BaseDataType.BOOL, BaseDataType.BYTE, BaseDataType.UBYTE -> numElements
                    BaseDataType.UWORD, BaseDataType.WORD -> numElements * 2
                    BaseDataType.LONG -> numElements * 4
                    BaseDataType.FLOAT -> numElements * 5
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

    // ============================================================================
    // BaseDataType Extension Property Tests
    // ============================================================================

    test("isByte extension") {
        BaseDataType.UBYTE.isByte.shouldBeTrue()
        BaseDataType.BYTE.isByte.shouldBeTrue()
        BaseDataType.UWORD.isByte.shouldBeFalse()
        BaseDataType.WORD.isByte.shouldBeFalse()
    }

    test("isByteOrBool extension") {
        BaseDataType.UBYTE.isByteOrBool.shouldBeTrue()
        BaseDataType.BYTE.isByteOrBool.shouldBeTrue()
        BaseDataType.BOOL.isByteOrBool.shouldBeTrue()
        BaseDataType.UWORD.isByteOrBool.shouldBeFalse()
    }

    test("isWord extension") {
        BaseDataType.UWORD.isWord.shouldBeTrue()
        BaseDataType.WORD.isWord.shouldBeTrue()
        BaseDataType.UBYTE.isWord.shouldBeFalse()
    }

    test("isInteger extension") {
        BaseDataType.UBYTE.isInteger.shouldBeTrue()
        BaseDataType.WORD.isInteger.shouldBeTrue()
        BaseDataType.LONG.isInteger.shouldBeTrue()
        BaseDataType.FLOAT.isInteger.shouldBeFalse()
    }

    test("isNumeric extension") {
        BaseDataType.UBYTE.isNumeric.shouldBeTrue()
        BaseDataType.FLOAT.isNumeric.shouldBeTrue()
        BaseDataType.BOOL.isNumeric.shouldBeFalse()
    }

    test("isSigned extension") {
        BaseDataType.BYTE.isSigned.shouldBeTrue()
        BaseDataType.WORD.isSigned.shouldBeTrue()
        BaseDataType.LONG.isSigned.shouldBeTrue()
        BaseDataType.UBYTE.isSigned.shouldBeFalse()
    }

    test("isArray extension") {
        BaseDataType.ARRAY.isArray.shouldBeTrue()
        BaseDataType.ARRAY_SPLITW.isArray.shouldBeTrue()
        BaseDataType.ARRAY_POINTER.isArray.shouldBeTrue()
        BaseDataType.UBYTE.isArray.shouldBeFalse()
    }

    test("isPointer extension") {
        BaseDataType.POINTER.isPointer.shouldBeTrue()
        BaseDataType.UBYTE.isPointer.shouldBeFalse()
    }

    test("isSplitWordArray extension") {
        BaseDataType.ARRAY_SPLITW.isSplitWordArray.shouldBeTrue()
        BaseDataType.ARRAY_POINTER.isSplitWordArray.shouldBeTrue()
        BaseDataType.ARRAY.isSplitWordArray.shouldBeFalse()
    }

    // ============================================================================
    // DataType Companion Object Tests
    // ============================================================================

    test("DataType.forDt creates simple types") {
        DataType.forDt(BaseDataType.UBYTE) shouldBe DataType.UBYTE
        DataType.forDt(BaseDataType.WORD) shouldBe DataType.WORD
        DataType.forDt(BaseDataType.LONG) shouldBe DataType.LONG
        DataType.forDt(BaseDataType.FLOAT) shouldBe DataType.FLOAT
    }

    test("DataType.forDt throws for struct instance") {
        shouldThrow<NotImplementedError> {
            DataType.forDt(BaseDataType.STRUCT_INSTANCE)
        }
    }

    test("DataType.arrayFor creates array types") {
        val ubyteArray = DataType.arrayFor(BaseDataType.UBYTE)
        ubyteArray.base shouldBe BaseDataType.ARRAY
        ubyteArray.sub shouldBe BaseDataType.UBYTE

        val wordArraySplit = DataType.arrayFor(BaseDataType.WORD)
        wordArraySplit.base shouldBe BaseDataType.ARRAY_SPLITW
        wordArraySplit.sub shouldBe BaseDataType.WORD
    }

    test("DataType.pointer creates pointer types") {
        val ptrByte = DataType.pointer(BaseDataType.UBYTE)
        ptrByte.base shouldBe BaseDataType.POINTER
        ptrByte.sub shouldBe BaseDataType.UBYTE
    }

    // ============================================================================
    // DataType Property Tests
    // ============================================================================

    test("DataType.isBasic property") {
        DataType.UBYTE.isBasic.shouldBeTrue()
        DataType.WORD.isBasic.shouldBeTrue()
        DataType.STR.isBasic.shouldBeFalse()
        DataType.arrayFor(BaseDataType.UBYTE).isBasic.shouldBeFalse()
    }

    test("DataType.isByte property") {
        DataType.UBYTE.isByte.shouldBeTrue()
        DataType.BYTE.isByte.shouldBeTrue()
        DataType.WORD.isByte.shouldBeFalse()
    }

    test("DataType.isNumeric property") {
        DataType.UBYTE.isNumeric.shouldBeTrue()
        DataType.FLOAT.isNumeric.shouldBeTrue()
        DataType.BOOL.isNumeric.shouldBeFalse()
        DataType.STR.isNumeric.shouldBeFalse()
    }

    test("DataType.isArray property") {
        DataType.arrayFor(BaseDataType.UBYTE).isArray.shouldBeTrue()
        DataType.UBYTE.isArray.shouldBeFalse()
    }

    test("DataType.isPointer property") {
        DataType.pointer(BaseDataType.UBYTE).isPointer.shouldBeTrue()
        DataType.UBYTE.isPointer.shouldBeFalse()
    }

    test("DataType.isString property") {
        DataType.STR.isString.shouldBeTrue()
        DataType.UBYTE.isString.shouldBeFalse()
    }

    test("DataType.isPassByRef property") {
        DataType.STR.isPassByRef.shouldBeTrue()
        DataType.arrayFor(BaseDataType.UBYTE).isPassByRef.shouldBeTrue()
        DataType.UBYTE.isPassByRef.shouldBeFalse()
        DataType.pointer(BaseDataType.UBYTE).isPassByRef.shouldBeFalse()
    }

    // ============================================================================
    // DataType Method Tests
    // ============================================================================

    test("DataType.elementType returns element type for arrays") {
        DataType.arrayFor(BaseDataType.UBYTE).elementType() shouldBe DataType.UBYTE
        DataType.arrayFor(BaseDataType.WORD).elementType() shouldBe DataType.WORD
        DataType.STR.elementType() shouldBe DataType.UBYTE
    }

    test("DataType.elementType throws for non-arrays") {
        shouldThrow<IllegalArgumentException> {
            DataType.UBYTE.elementType()
        }
    }

    test("DataType.dereference for pointers") {
        DataType.pointer(BaseDataType.UBYTE).dereference() shouldBe DataType.UBYTE
        DataType.pointer(BaseDataType.WORD).dereference() shouldBe DataType.WORD
        DataType.UWORD.dereference() shouldBe DataType.UBYTE
    }

    test("DataType.size with memsizer") {
        DataType.UBYTE.size(dummyMemSizer) shouldBe 1
        DataType.WORD.size(dummyMemSizer) shouldBe 2
        DataType.LONG.size(dummyMemSizer) shouldBe 4
        DataType.FLOAT.size(dummyMemSizer) shouldBe 5
    }

    // ============================================================================
    // DataType Comparison Tests
    // ============================================================================

    test("DataType equals for same types") {
        DataType.UBYTE shouldBe DataType.UBYTE
        DataType.WORD shouldBe DataType.WORD
        DataType.STR shouldBe DataType.STR
    }

    test("DataType equals for different types") {
        DataType.UBYTE shouldNotBe DataType.BYTE
        DataType.UBYTE shouldNotBe DataType.WORD
    }

    test("BaseDataType.largerSizeThan") {
        BaseDataType.WORD.largerSizeThan(BaseDataType.UBYTE).shouldBeTrue()
        BaseDataType.LONG.largerSizeThan(BaseDataType.WORD).shouldBeTrue()
        BaseDataType.UBYTE.largerSizeThan(BaseDataType.WORD).shouldBeFalse()
    }

    test("BaseDataType.equalsSize") {
        BaseDataType.UBYTE.equalsSize(BaseDataType.UBYTE).shouldBeTrue()
        BaseDataType.UBYTE.equalsSize(BaseDataType.BYTE).shouldBeTrue()
        BaseDataType.WORD.equalsSize(BaseDataType.UWORD).shouldBeTrue()
        BaseDataType.UBYTE.equalsSize(BaseDataType.WORD).shouldBeFalse()
    }

    // ============================================================================
    // DataType.isAssignableTo Tests
    // ============================================================================

    test("bool assignability") {
        DataType.BOOL.isAssignableTo(DataType.BOOL).shouldBeTrue()
        DataType.BOOL.isAssignableTo(DataType.UBYTE).shouldBeFalse()
    }

    test("ubyte assignability") {
        DataType.UBYTE.isAssignableTo(DataType.UBYTE).shouldBeTrue()
        DataType.UBYTE.isAssignableTo(DataType.UWORD).shouldBeTrue()
        DataType.UBYTE.isAssignableTo(DataType.LONG).shouldBeTrue()
        DataType.UBYTE.isAssignableTo(DataType.FLOAT).shouldBeTrue()
    }

    test("byte assignability") {
        DataType.BYTE.isAssignableTo(DataType.BYTE).shouldBeTrue()
        DataType.BYTE.isAssignableTo(DataType.WORD).shouldBeTrue()
        DataType.BYTE.isAssignableTo(DataType.LONG).shouldBeTrue()
    }

    test("uword assignability") {
        DataType.UWORD.isAssignableTo(DataType.UWORD).shouldBeTrue()
        DataType.UWORD.isAssignableTo(DataType.LONG).shouldBeTrue()
        DataType.UWORD.isAssignableTo(DataType.FLOAT).shouldBeTrue()
    }

    test("string assignability") {
        DataType.STR.isAssignableTo(DataType.STR).shouldBeTrue()
        DataType.STR.isAssignableTo(DataType.UWORD).shouldBeTrue()
    }

    test("pointer assignability") {
        val ptr = DataType.pointer(BaseDataType.UBYTE)
        ptr.isAssignableTo(DataType.UWORD).shouldBeTrue()
        ptr.isAssignableTo(DataType.LONG).shouldBeTrue()
        ptr.isAssignableTo(DataType.pointer(BaseDataType.UBYTE)).shouldBeTrue()
    }

    // ============================================================================
    // DataType.toString Tests
    // ============================================================================

    test("DataType.toString for basic types") {
        DataType.UBYTE.toString() shouldBe "ubyte"
        DataType.WORD.toString() shouldBe "word"
        DataType.LONG.toString() shouldBe "long"
        DataType.FLOAT.toString() shouldBe "float"
    }

    test("DataType.toString for arrays") {
        DataType.arrayFor(BaseDataType.UBYTE).toString() shouldBe "ubyte[]"
        DataType.arrayFor(BaseDataType.WORD).toString() shouldBe "word[] (split)"
    }
})
