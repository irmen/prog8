import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.vm.Instruction
import prog8.vm.Opcode
import prog8.vm.VmDataType
import prog8.vm.instructionFormats


class TestInstructions: FunSpec({

    test("simple") {
        val ins = Instruction(Opcode.NOP)
        ins.opcode shouldBe Opcode.NOP
        ins.type shouldBe null
        ins.reg1 shouldBe null
        ins.reg2 shouldBe null
        ins.value shouldBe null
        ins.labelSymbol shouldBe null
        ins.toString() shouldBe "nop"
    }

    test("with value") {
        val ins = Instruction(Opcode.BZ, VmDataType.BYTE, reg1=42, value = 9999)
        ins.opcode shouldBe Opcode.BZ
        ins.type shouldBe VmDataType.BYTE
        ins.reg1 shouldBe 42
        ins.reg2 shouldBe null
        ins.value shouldBe 9999
        ins.labelSymbol shouldBe null
        ins.toString() shouldBe "bz.b r42,9999"
    }

    test("with label") {
        val ins = Instruction(Opcode.BZ, VmDataType.WORD, reg1=11, labelSymbol = listOf("a","b","c"))
        ins.opcode shouldBe Opcode.BZ
        ins.type shouldBe VmDataType.WORD
        ins.reg1 shouldBe 11
        ins.reg2 shouldBe null
        ins.value shouldBe null
        ins.labelSymbol shouldBe listOf("a","b","c")
        ins.toString() shouldBe "bz.w r11,_a.b.c"
    }

    test("missing type should fail") {
        shouldThrow<IllegalArgumentException> {
            Instruction(Opcode.BZ, reg1=42, value=9999)
        }
    }

    test("missing registers should fail") {
        shouldThrow<IllegalArgumentException> {
            Instruction(Opcode.BZ, VmDataType.BYTE, value=9999)
        }
    }

    test("missing value should fail") {
        shouldThrow<IllegalArgumentException> {
            Instruction(Opcode.BZ, VmDataType.BYTE, reg1=42)
        }
    }

    test("all instructionformats") {
        Opcode.values().forEach {
            instructionFormats[it] shouldNotBe null
        }
    }
})
