package prog8tests.codegencpu6502

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.BuiltinFunctions
import prog8.code.core.DataType
import prog8.code.core.NumericDatatypesNoBool
import prog8.code.core.RegisterOrPair

class TestBuiltinFunctions: FunSpec({

    test("pure func with fixed type") {
        val func = BuiltinFunctions.getValue("sgn")
        func.parameters.size shouldBe 1
        func.parameters[0].name shouldBe "value"
        func.parameters[0].possibleDatatypes shouldBe NumericDatatypesNoBool
        func.pure shouldBe true
        func.returnType shouldBe DataType.BYTE

        val conv = func.callConvention(listOf(DataType.UBYTE))
        conv.params.size shouldBe 1
        conv.params[0].dt shouldBe DataType.UBYTE
        conv.params[0].reg shouldBe RegisterOrPair.A
        conv.params[0].variable shouldBe false
        conv.returns.dt shouldBe DataType.BYTE
        conv.returns.floatFac1 shouldBe false
        conv.returns.reg shouldBe RegisterOrPair.A
    }

    test("not-pure func with varying result value type") {
        val func = BuiltinFunctions.getValue("cmp")
        func.parameters.size shouldBe 2
        func.pure shouldBe false
        func.returnType shouldBe null

        val conv = func.callConvention(listOf(DataType.UWORD, DataType.UWORD))
        conv.params.size shouldBe 2
        conv.returns.dt shouldBe null
        conv.returns.floatFac1 shouldBe false
        conv.returns.reg shouldBe null
    }

    test("func without return type") {
        val func = BuiltinFunctions.getValue("poke")
        func.parameters.size shouldBe 2
        func.parameters[0].name shouldBe "address"
        func.parameters[0].possibleDatatypes shouldBe arrayOf(DataType.UWORD)
        func.parameters[1].name shouldBe "value"
        func.parameters[1].possibleDatatypes shouldBe arrayOf(DataType.UBYTE)
        func.pure shouldBe false
        func.returnType shouldBe null

        val conv = func.callConvention(listOf(DataType.UWORD, DataType.UBYTE))
        conv.params.size shouldBe 2
        conv.params[0].dt shouldBe DataType.UWORD
        conv.params[0].reg shouldBe null
        conv.params[0].variable shouldBe true
        conv.params[1].dt shouldBe DataType.UBYTE
        conv.params[1].reg shouldBe null
        conv.params[1].variable shouldBe true
        conv.returns.dt shouldBe null
        conv.returns.floatFac1 shouldBe false
        conv.returns.reg shouldBe null
    }
})

