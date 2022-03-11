package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.DataType
import prog8.code.core.NumericDatatypes
import prog8.code.core.RegisterOrPair
import prog8.compiler.BuiltinFunctions

class TestBuiltinFunctions: FunSpec({

    test("pure func with fixed type") {
        val func = BuiltinFunctions.getValue("sin8u")
        func.name shouldBe "sin8u"
        func.parameters.size shouldBe 1
        func.parameters[0].name shouldBe "angle8"
        func.parameters[0].possibleDatatypes shouldBe arrayOf(DataType.UBYTE)
        func.pure shouldBe true
        func.hasReturn shouldBe true
        func.returnType shouldBe DataType.UBYTE

        val conv = func.callConvention(listOf(DataType.UBYTE))
        conv.params.size shouldBe 1
        conv.params[0].dt shouldBe DataType.UBYTE
        conv.params[0].reg shouldBe RegisterOrPair.A
        conv.params[0].variable shouldBe false
        conv.returns.dt shouldBe DataType.UBYTE
        conv.returns.floatFac1 shouldBe false
        conv.returns.reg shouldBe RegisterOrPair.A
    }

    test("not-pure func with fixed type") {
        val func = BuiltinFunctions.getValue("rnd")
        func.name shouldBe "rnd"
        func.parameters.size shouldBe 0
        func.pure shouldBe false
        func.hasReturn shouldBe true
        func.returnType shouldBe DataType.UBYTE

        val conv = func.callConvention(emptyList())
        conv.params.size shouldBe 0
        conv.returns.dt shouldBe DataType.UBYTE
        conv.returns.floatFac1 shouldBe false
        conv.returns.reg shouldBe RegisterOrPair.A
    }

    test("func without return type") {
        val func = BuiltinFunctions.getValue("poke")
        func.name shouldBe "poke"
        func.parameters.size shouldBe 2
        func.parameters[0].name shouldBe "address"
        func.parameters[0].possibleDatatypes shouldBe arrayOf(DataType.UWORD)
        func.parameters[1].name shouldBe "value"
        func.parameters[1].possibleDatatypes shouldBe arrayOf(DataType.UBYTE)
        func.pure shouldBe false
        func.hasReturn shouldBe false
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

    test("func with variable return type") {
        val func = BuiltinFunctions.getValue("abs")
        func.name shouldBe "abs"
        func.parameters.size shouldBe 1
        func.parameters[0].name shouldBe "value"
        func.parameters[0].possibleDatatypes.toSet() shouldBe NumericDatatypes.toSet()
        func.pure shouldBe true
        func.hasReturn shouldBe true
        func.returnType shouldBe null

        val conv = func.callConvention(listOf(DataType.UWORD))
        conv.params.size shouldBe 1
        conv.params[0].dt shouldBe DataType.UWORD
        conv.params[0].reg shouldBe RegisterOrPair.AY
        conv.params[0].variable shouldBe false
        conv.returns.dt shouldBe DataType.UWORD
        conv.returns.floatFac1 shouldBe false
        conv.returns.reg shouldBe RegisterOrPair.AY
    }
})

