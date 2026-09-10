import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.intermediate.*


class TestInstructions: FunSpec({

    test("simple") {
        val ins = IRInstructions.simple(Opcode.NOP)
        ins.opcode shouldBe Opcode.NOP
        ins.type shouldBe null
        ins.dest shouldBe null
        ins.srcA shouldBe null
        ins.memory shouldBe null
        ins.immediate shouldBe null
        ins.target shouldBe null
        ins.callSite shouldBe null
        ins.registerAccesses shouldBe emptyList()
        ins.toString() shouldBe "nop"
    }

    test("with immediate value") {
        val ins = IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.BYTE, destination = 42, value = 0)
        ins.opcode shouldBe Opcode.ADD
        ins.type shouldBe IRDataType.BYTE
        ins.requireDest().registerNumber shouldBe 42
        ins.requireDest().direction shouldBe OperandDirection.USE_DEF
        ins.requireImmediate() shouldBe ImmediateOperand.Integer(0, IRDataType.BYTE)
        ins.toString() shouldBe "add.b r42.b,#0.b"
    }

    test("with symbol address") {
        val ins = IRInstructions.loadAddress(IRDataType.WORD, 11, "a.b.c")
        ins.opcode shouldBe Opcode.LOAD
        ins.requireImmediate() shouldBe ImmediateOperand.SymbolAddress("a.b.c", 0, IRDataType.WORD)
        ins.toString() shouldBe "load.w r11.w,#a.b.c"

        val withOffset = IRInstructions.loadAddress(IRDataType.WORD, 11, "a.b.c", 4)
        withOffset.toString() shouldBe "load.w r11.w,#a.b.c+4"
    }

    test("with output registers") {
        val ins = IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, destination = 11, source = 22)
        ins.requireDest().direction shouldBe OperandDirection.USE_DEF
        ins.requireSrcA().direction shouldBe OperandDirection.USE
        ins.uses shouldBe setOf(VirtualRegister.int(11), VirtualRegister.int(22))
        ins.definitions shouldBe setOf(VirtualRegister.int(11))
        ins.toString() shouldBe "addr.w r11.w,r22.w"

        val ins2 = IRInstructions.binary(Opcode.SQRT, IRDataType.BYTE, destination = 11, source = 22)
        ins2.requireDest().direction shouldBe OperandDirection.DEF
        ins2.requireSrcA().direction shouldBe OperandDirection.USE
        ins2.definitions shouldBe setOf(VirtualRegister.int(11))
        ins2.toString() shouldBe "sqrt.b r11.b,r22.b"
    }

    test("with float regs") {
        val ins = IRInstructions.binary(Opcode.FSIN, IRDataType.FLOAT, destination = 1, source = 2)
        ins.type shouldBe IRDataType.FLOAT
        ins.requireDest().register shouldBe VirtualRegister.float(1)
        ins.requireSrcA().register shouldBe VirtualRegister.float(2)
        ins.toString() shouldBe "fsin.f fr1.f,fr2.f"
    }

    test("typed binary factory infers conversion instruction types") {
        val sign = IRInstructions.binary(
            Opcode.SGN,
            IRInstructions.operandFor(Opcode.SGN, IRDataType.WORD, InstructionSlot.DEST, 1),
            IRInstructions.operandFor(Opcode.SGN, IRDataType.WORD, InstructionSlot.SRC_A, 2)
        )
        sign.type shouldBe IRDataType.WORD
        sign.requireDest().type shouldBe IRDataType.BYTE
        sign.requireSrcA().type shouldBe IRDataType.WORD

        val floatFromByte = IRInstructions.binary(
            Opcode.FFROMUB,
            IRInstructions.operandFor(Opcode.FFROMUB, IRDataType.FLOAT, InstructionSlot.DEST, 1),
            IRInstructions.operandFor(Opcode.FFROMUB, IRDataType.FLOAT, InstructionSlot.SRC_A, 2)
        )
        floatFromByte.type shouldBe IRDataType.FLOAT
        floatFromByte.requireDest().register shouldBe VirtualRegister.float(1)
        floatFromByte.requireSrcA().type shouldBe IRDataType.BYTE
    }

    test("missing type should fail") {
        shouldThrow<IllegalArgumentException> {
            IRInstruction(Opcode.ADD, dest = IRInstructions.operandFor(Opcode.ADD, IRDataType.BYTE, InstructionSlot.DEST, 42))
        }
    }

    test("missing registers should fail") {
        shouldThrow<IllegalArgumentException> {
            IRInstruction(Opcode.ADD, IRDataType.BYTE, immediate = ImmediateOperand.Integer(0, IRDataType.BYTE))
        }
    }

    test("missing memory operand should fail") {
        shouldThrow<IllegalArgumentException> {
            IRInstruction(Opcode.INCM, IRDataType.BYTE)
        }
    }

    test("wrong operand kind should fail") {
        shouldThrow<IllegalArgumentException> {
            // LOADM needs a direct memory reference, not an indirect one
            IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, 1, IRMemory.indirect(2, 4))
        }
        shouldThrow<IllegalArgumentException> {
            // an immediate that doesn't fit the type
            IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.BYTE, destination = 1, value = 9999)
        }
    }

    test("baseline instruction shapes") {
        val loadx = IRInstructions.loadMemory(
            Opcode.LOADX, IRDataType.WORD, 1,
            IRMemory.indexed("main.items", 2, IRDataType.WORD, scale = 2, displacement = 4)
        )
        loadx.toString() shouldBe "loadx.w r1.w,[main.items+4+r2.w*2]"

        val loadi = IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, 3, IRMemory.indirect(4, 8))
        loadi.toString() shouldBe "loadi.b r3.b,[r4.p+8]"

        val loadhr = IRInstructions.hardwareLoad(IRDataType.WORD, 5, CallingConventionSlot(10))
        loadhr.toString() shouldBe "loadhr.w r5.w,s10.w"

        val calli = IRInstructions.call(Opcode.CALLI, CallSite(CallTarget.Direct(codeIndirect(6))))
        calli.toString() shouldBe "calli (r6.p)()"
    }

    test("with symbol offset in a memory reference") {
        val i1 = IRInstructions.memoryOp(Opcode.ADDM, IRDataType.BYTE, IRMemory.direct("symbol", 99), source = 1)
        val memory = i1.requireMemory() as MemoryReference.Direct
        memory.symbolName shouldBe "symbol"
        memory.displacement shouldBe 99
        i1.toString() shouldBe "addm.b r1.b,[symbol+99]"

        val i2 = IRInstructions.memoryOp(Opcode.ADDM, IRDataType.BYTE, IRMemory.direct("symbol"), source = 1)
        (i2.requireMemory() as MemoryReference.Direct).displacement shouldBe 0
        i2.toString() shouldBe "addm.b r1.b,[symbol]"
    }

    test("register accounting uses distinct integer and float identities") {
        val reads = mutableMapOf<VirtualRegister, Int>()
        val writes = mutableMapOf<VirtualRegister, Int>()
        val types = mutableMapOf<VirtualRegister, IRDataType>()
        IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 5, 6).addUsedRegistersCounts(reads, writes, types, null)
        IRInstructions.binary(Opcode.FSIN, IRDataType.FLOAT, 5, 6).addUsedRegistersCounts(reads, writes, types, null)
        reads.keys shouldBe setOf(
            VirtualRegister.int(5), VirtualRegister.int(6),
            VirtualRegister.float(6)
        )
        writes.keys shouldBe setOf(VirtualRegister.int(5), VirtualRegister.float(5))
        types shouldBe mapOf(
            VirtualRegister.int(5) to IRDataType.WORD,
            VirtualRegister.int(6) to IRDataType.WORD
        )
    }

    test("instructions can be retargeted") {
        val jump = IRInstructions.jump(codeLabel("main.first"))
        jump.withTarget(codeLabel("main.second")).requireTarget() shouldBe CodeReference.Label("main.second")

        val call = IRInstructions.call(CallSite(CallTarget.Direct(codeLabel("main.first"))))
        call.withTarget(codeLabel("main.second")).requireCallSite().codeReference shouldBe CodeReference.Label("main.second")
    }
})
