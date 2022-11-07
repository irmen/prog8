import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.intermediate.*


class TestInstructions: FunSpec({

    test("simple") {
        val ins = IRInstruction(Opcode.NOP)
        ins.opcode shouldBe Opcode.NOP
        ins.type shouldBe null
        ins.reg1direction shouldBe OperandDirection.UNUSED
        ins.fpReg1direction shouldBe OperandDirection.UNUSED
        ins.reg1 shouldBe null
        ins.reg2 shouldBe null
        ins.value shouldBe null
        ins.labelSymbol shouldBe null
        ins.toString() shouldBe "nop"
    }

    test("with value") {
        val ins = IRInstruction(Opcode.BZ, IRDataType.BYTE, reg1=42, value = 99)
        ins.opcode shouldBe Opcode.BZ
        ins.type shouldBe IRDataType.BYTE
        ins.reg1direction shouldBe OperandDirection.INPUT
        ins.fpReg1direction shouldBe OperandDirection.UNUSED
        ins.reg1 shouldBe 42
        ins.reg2 shouldBe null
        ins.value shouldBe 99
        ins.labelSymbol shouldBe null
        ins.toString() shouldBe "bz.b r42,$63"
    }

    test("with label") {
        val ins = IRInstruction(Opcode.BZ, IRDataType.WORD, reg1=11, labelSymbol = "a.b.c")
        ins.opcode shouldBe Opcode.BZ
        ins.type shouldBe IRDataType.WORD
        ins.reg1direction shouldBe OperandDirection.INPUT
        ins.fpReg1direction shouldBe OperandDirection.UNUSED
        ins.reg1 shouldBe 11
        ins.reg2 shouldBe null
        ins.value shouldBe null
        ins.labelSymbol shouldBe "a.b.c"
        ins.toString() shouldBe "bz.w r11,a.b.c"
    }

    test("with output registers") {
        val ins = IRInstruction(Opcode.ADDR, IRDataType.WORD, reg1=11, reg2=22)
        ins.opcode shouldBe Opcode.ADDR
        ins.type shouldBe IRDataType.WORD
        ins.reg1direction shouldBe OperandDirection.INOUT
        ins.reg2direction shouldBe OperandDirection.INPUT
        ins.fpReg1direction shouldBe OperandDirection.UNUSED
        ins.fpReg2direction shouldBe OperandDirection.UNUSED
        ins.reg1 shouldBe 11
        ins.reg2 shouldBe 22
        ins.value shouldBe null
        ins.labelSymbol shouldBe null
        ins.toString() shouldBe "addr.w r11,r22"

        val ins2 = IRInstruction(Opcode.SQRT, IRDataType.BYTE, reg1=11, reg2=22)
        ins2.opcode shouldBe Opcode.SQRT
        ins2.type shouldBe IRDataType.BYTE
        ins2.reg1direction shouldBe OperandDirection.OUTPUT
        ins2.reg2direction shouldBe OperandDirection.INPUT
        ins2.fpReg1direction shouldBe OperandDirection.UNUSED
        ins2.fpReg2direction shouldBe OperandDirection.UNUSED
        ins2.reg1 shouldBe 11
        ins2.reg2 shouldBe 22
        ins2.value shouldBe null
        ins2.labelSymbol shouldBe null
        ins2.toString() shouldBe "sqrt.b r11,r22"
    }

    test("with float regs") {
        val ins = IRInstruction(Opcode.FSIN, IRDataType.FLOAT, fpReg1 = 1, fpReg2 = 2)
        ins.opcode shouldBe Opcode.FSIN
        ins.type shouldBe IRDataType.FLOAT
        ins.reg1direction shouldBe OperandDirection.UNUSED
        ins.reg2direction shouldBe OperandDirection.UNUSED
        ins.fpReg1direction shouldBe OperandDirection.OUTPUT
        ins.fpReg2direction shouldBe OperandDirection.INPUT
        ins.fpReg1 shouldBe 1
        ins.fpReg2 shouldBe 2
        ins.reg1 shouldBe null
        ins.reg2 shouldBe null
        ins.value shouldBe null
        ins.labelSymbol shouldBe null
        ins.toString() shouldBe "fsin.f fr1,fr2"
    }


    test("missing type should fail") {
        shouldThrow<IllegalArgumentException> {
            IRInstruction(Opcode.BZ, reg1=42, value=99)
        }
    }

    test("missing registers should fail") {
        shouldThrowWithMessage<IllegalArgumentException>("missing reg1") {
            IRInstruction(Opcode.BZ, IRDataType.BYTE, value=99)
        }
    }

    test("missing value should fail") {
        shouldThrowWithMessage<IllegalArgumentException>("missing a value or labelsymbol") {
            IRInstruction(Opcode.BZ, IRDataType.BYTE, reg1=42)
        }
    }

    test("all instructionformats") {
        instructionFormats.size shouldBe Opcode.values().size
        Opcode.values().forEach {
            val fmt = instructionFormats.getValue(it)
            fmt.values.forEach { format ->
                require(format.reg2==OperandDirection.UNUSED || format.reg2==OperandDirection.INPUT) {"reg2 can only be used as input"}
                require(format.fpReg2==OperandDirection.UNUSED || format.fpReg2==OperandDirection.INPUT) {"fpReg2 can only be used as input"}
            }
        }
     }
})
