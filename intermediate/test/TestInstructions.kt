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
        ins.address shouldBe null
        ins.immediate shouldBe null
        ins.immediateFp shouldBe null
        ins.labelSymbol shouldBe null
        ins.toString() shouldBe "nop"
    }

    test("with value") {
        val ins = IRInstruction(Opcode.ADD, IRDataType.BYTE, reg1=42, immediate = 0, address = 99)
        ins.opcode shouldBe Opcode.ADD
        ins.type shouldBe IRDataType.BYTE
        ins.reg1direction shouldBe OperandDirection.READWRITE
        ins.fpReg1direction shouldBe OperandDirection.UNUSED
        ins.reg1 shouldBe 42
        ins.reg2 shouldBe null
        ins.address shouldBe 99
        ins.immediate shouldBe 0
        ins.immediateFp shouldBe null
        ins.labelSymbol shouldBe null
        ins.toString() shouldBe "add.b r42,#0,$63"
    }

    test("with label") {
        val ins = IRInstruction(Opcode.ADD, IRDataType.WORD, reg1=11, immediate = 0, labelSymbol = "a.b.c")
        ins.opcode shouldBe Opcode.ADD
        ins.type shouldBe IRDataType.WORD
        ins.reg1direction shouldBe OperandDirection.READWRITE
        ins.fpReg1direction shouldBe OperandDirection.UNUSED
        ins.reg1 shouldBe 11
        ins.reg2 shouldBe null
        ins.address shouldBe null
        ins.immediate shouldBe 0
        ins.immediateFp shouldBe null
        ins.labelSymbol shouldBe "a.b.c"
        ins.toString() shouldBe "add.w r11,#0,a.b.c"
    }

    test("with output registers") {
        val ins = IRInstruction(Opcode.ADDR, IRDataType.WORD, reg1=11, reg2=22)
        ins.opcode shouldBe Opcode.ADDR
        ins.type shouldBe IRDataType.WORD
        ins.reg1direction shouldBe OperandDirection.READWRITE
        ins.reg2direction shouldBe OperandDirection.READ
        ins.fpReg1direction shouldBe OperandDirection.UNUSED
        ins.fpReg2direction shouldBe OperandDirection.UNUSED
        ins.reg1 shouldBe 11
        ins.reg2 shouldBe 22
        ins.address shouldBe null
        ins.immediate shouldBe null
        ins.immediateFp shouldBe null
        ins.labelSymbol shouldBe null
        ins.toString() shouldBe "addr.w r11,r22"

        val ins2 = IRInstruction(Opcode.SQRT, IRDataType.BYTE, reg1=11, reg2=22)
        ins2.opcode shouldBe Opcode.SQRT
        ins2.type shouldBe IRDataType.BYTE
        ins2.reg1direction shouldBe OperandDirection.WRITE
        ins2.reg2direction shouldBe OperandDirection.READ
        ins2.fpReg1direction shouldBe OperandDirection.UNUSED
        ins2.fpReg2direction shouldBe OperandDirection.UNUSED
        ins2.reg1 shouldBe 11
        ins2.reg2 shouldBe 22
        ins.address shouldBe null
        ins.immediate shouldBe null
        ins.immediateFp shouldBe null
        ins2.labelSymbol shouldBe null
        ins2.toString() shouldBe "sqrt.b r11,r22"
    }

    test("with float regs") {
        val ins = IRInstruction(Opcode.FSIN, IRDataType.FLOAT, fpReg1 = 1, fpReg2 = 2)
        ins.opcode shouldBe Opcode.FSIN
        ins.type shouldBe IRDataType.FLOAT
        ins.reg1direction shouldBe OperandDirection.UNUSED
        ins.reg2direction shouldBe OperandDirection.UNUSED
        ins.fpReg1direction shouldBe OperandDirection.WRITE
        ins.fpReg2direction shouldBe OperandDirection.READ
        ins.fpReg1 shouldBe 1
        ins.fpReg2 shouldBe 2
        ins.reg1 shouldBe null
        ins.reg2 shouldBe null
        ins.address shouldBe null
        ins.immediate shouldBe null
        ins.immediateFp shouldBe null
        ins.labelSymbol shouldBe null
        ins.toString() shouldBe "fsin.f fr1,fr2"
    }


    test("missing type should fail") {
        shouldThrow<IllegalArgumentException> {
            IRInstruction(Opcode.ADD, reg1=42, address=99)
        }
    }

    test("missing registers should fail") {
        shouldThrowWithMessage<IllegalArgumentException>("missing reg1") {
            IRInstruction(Opcode.ADD, IRDataType.BYTE, immediate = 0, address=99)
        }
    }

    test("missing address should fail") {
        shouldThrowWithMessage<IllegalArgumentException>("missing an address or labelsymbol") {
            IRInstruction(Opcode.INCM, IRDataType.BYTE)
        }
    }

    test("all instructionformats") {
        instructionFormats.size shouldBe Opcode.entries.size
        Opcode.entries.forEach {
            val fmt = instructionFormats.getValue(it)
            fmt.values.forEach { format ->
                require(format.reg2==OperandDirection.UNUSED || format.reg2==OperandDirection.READ || format.reg2==OperandDirection.READWRITE) {"reg2 can only be used as input or readwrite"}
                require(format.fpReg2==OperandDirection.UNUSED || format.fpReg2==OperandDirection.READ || format.fpReg2==OperandDirection.READWRITE) {"fpReg2 can only be used as input or readwrite"}
            }
        }
    }

    test("with symbol offset") {
        val i1 = IRInstruction(Opcode.ADDM, IRDataType.BYTE, reg1 = 1, labelSymbol = "symbol", symbolOffset = 99)
        i1.labelSymbol shouldBe "symbol"
        i1.labelSymbolOffset shouldBe 99

        val i2 = IRInstruction(Opcode.ADDM, IRDataType.BYTE, reg1 = 1, labelSymbol = "symbol", symbolOffset = 0)
        i2.labelSymbol shouldBe "symbol"
        i2.labelSymbolOffset shouldBe null

        shouldThrowWithMessage<IllegalArgumentException>("labelsymbol offset inconsistency") {
            IRInstruction(Opcode.ADDR, IRDataType.BYTE, reg1 = 1, reg2 = 2, symbolOffset = 99)
        }
    }
})
