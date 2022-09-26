import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.intermediate.*


class TestInstructions: FunSpec({

    test("simple") {
        val ins = IRInstruction(Opcode.NOP)
        ins.opcode shouldBe Opcode.NOP
        ins.type shouldBe null
        ins.reg1 shouldBe null
        ins.reg2 shouldBe null
        ins.value shouldBe null
        ins.labelSymbol shouldBe null
        ins.reg1direction shouldBe OperandDirection.INPUT
        ins.fpReg1direction shouldBe OperandDirection.INPUT
        ins.toString() shouldBe "nop"
    }

    test("with value") {
        val ins = IRInstruction(Opcode.BZ, VmDataType.BYTE, reg1=42, value = 99)
        ins.opcode shouldBe Opcode.BZ
        ins.type shouldBe VmDataType.BYTE
        ins.reg1 shouldBe 42
        ins.reg2 shouldBe null
        ins.value shouldBe 99
        ins.labelSymbol shouldBe null
        ins.reg1direction shouldBe OperandDirection.INPUT
        ins.fpReg1direction shouldBe OperandDirection.INPUT
        ins.toString() shouldBe "bz.b r42,99"
    }

    test("with label") {
        val ins = IRInstruction(Opcode.BZ, VmDataType.WORD, reg1=11, labelSymbol = "a.b.c")
        ins.opcode shouldBe Opcode.BZ
        ins.type shouldBe VmDataType.WORD
        ins.reg1 shouldBe 11
        ins.reg2 shouldBe null
        ins.value shouldBe null
        ins.labelSymbol shouldBe "a.b.c"
        ins.reg1direction shouldBe OperandDirection.INPUT
        ins.fpReg1direction shouldBe OperandDirection.INPUT
        ins.toString() shouldBe "bz.w r11,_a.b.c"
    }

    test("with output registers") {
        val ins = IRInstruction(Opcode.ADDR, VmDataType.WORD, reg1=11, reg2=22)
        ins.opcode shouldBe Opcode.ADDR
        ins.type shouldBe VmDataType.WORD
        ins.reg1 shouldBe 11
        ins.reg2 shouldBe 22
        ins.value shouldBe null
        ins.labelSymbol shouldBe null
        ins.reg1direction shouldBe OperandDirection.INOUT
        ins.fpReg1direction shouldBe OperandDirection.INPUT
        ins.toString() shouldBe "addr.w r11,r22"

        val ins2 = IRInstruction(Opcode.SQRT, VmDataType.BYTE, reg1=11, reg2=22)
        ins2.opcode shouldBe Opcode.SQRT
        ins2.type shouldBe VmDataType.BYTE
        ins2.reg1 shouldBe 11
        ins2.reg2 shouldBe 22
        ins2.value shouldBe null
        ins2.labelSymbol shouldBe null
        ins2.reg1direction shouldBe OperandDirection.OUTPUT
        ins2.fpReg1direction shouldBe OperandDirection.INPUT
        ins2.toString() shouldBe "sqrt.b r11,r22"
    }


    test("missing type should fail") {
        shouldThrow<IllegalArgumentException> {
            IRInstruction(Opcode.BZ, reg1=42, value=9999)
        }
    }

    test("missing registers should fail") {
        shouldThrow<IllegalArgumentException> {
            IRInstruction(Opcode.BZ, VmDataType.BYTE, value=9999)
        }
    }

    test("missing value should fail") {
        shouldThrow<IllegalArgumentException> {
            IRInstruction(Opcode.BZ, VmDataType.BYTE, reg1=42)
        }
    }

    test("all instructionformats") {
        Opcode.values().forEach {
            instructionFormats[it] shouldNotBe null
        }
    }
})
