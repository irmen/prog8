import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.intermediate.*


class TestStructuredOperands: FunSpec({

    fun testIndexOperand() = RegisterOperand(
        VirtualRegister.IntReg(RegisterNum(3)),
        IRDataType.WORD,
        OperandRole.INDEX,
        OperandDirection.USE,
        AllocationHint.PREFER_DATA
    )

    test("integer and float registers are distinct") {
        val intReg: VirtualRegister = VirtualRegister.IntReg(RegisterNum(5))
        val floatReg: VirtualRegister = VirtualRegister.FloatReg(RegisterNum(5))
        intReg shouldNotBe floatReg
        intReg.number shouldBe floatReg.number
        intReg shouldBe VirtualRegister.int(5)
        floatReg shouldBe VirtualRegister.float(5)
    }

    test("float registers require the float type") {
        shouldThrow<IllegalArgumentException> {
            RegisterOperand(VirtualRegister.float(1), IRDataType.WORD, OperandRole.VALUE, OperandDirection.USE)
        }
        shouldThrow<IllegalArgumentException> {
            RegisterOperand(VirtualRegister.int(1), IRDataType.FLOAT, OperandRole.VALUE, OperandDirection.USE)
        }
    }

    test("indexed memory keeps base index scale and displacement") {
        val index = RegisterOperand(
            VirtualRegister.IntReg(RegisterNum(3)),
            IRDataType.WORD,
            OperandRole.INDEX,
            OperandDirection.USE,
            AllocationHint.PREFER_DATA
        )
        val ref = MemoryReference.Indexed(
            AddressBase.Symbol("main.items"),
            index,
            scale = 6,
            displacement = 4
        )
        ref.scale shouldBe 6
        ref.displacement shouldBe 4
        ref.symbolName shouldBe "main.items"
        ref.registers shouldBe listOf(index)
    }

    test("indexed scale must be in range") {
        shouldThrow<IllegalArgumentException> {
            MemoryReference.Indexed(
                AddressBase.Symbol("main.items"),
                testIndexOperand(),
                scale = 0
            )
        }
        shouldThrow<IllegalArgumentException> {
            MemoryReference.Indexed(
                AddressBase.Symbol("main.items"),
                testIndexOperand(),
                scale = 65536
            )
        }
    }

    test("embedded register operands have the required semantic role") {
        shouldThrow<IllegalArgumentException> {
            MemoryReference.Indexed(
                AddressBase.Symbol("main.items"),
                IRMemory.pointerOperand(3),
                scale = 2
            )
        }
        shouldThrow<IllegalArgumentException> {
            MemoryReference.Indirect(
                IRMemory.indexOperand(3, IRDataType.WORD)
            )
        }
        shouldThrow<IllegalArgumentException> {
            CodeReference.Indirect(IRMemory.pointerOperand(3))
        }
    }

    test("indirect displacement is limited to the loadi/storei range") {
        MemoryReference.Indirect(IRMemory.pointerOperand(2), 65535).displacement shouldBe 65535
        shouldThrow<IllegalArgumentException> {
            MemoryReference.Indirect(IRMemory.pointerOperand(2), -1)
        }
        shouldThrow<IllegalArgumentException> {
            MemoryReference.Indirect(IRMemory.pointerOperand(2), 65536)
        }
    }

    test("symbol names are validated") {
        shouldThrow<IllegalArgumentException> { AddressBase.Symbol("_underscore") }
        shouldThrow<IllegalArgumentException> { AddressBase.Symbol("\$d020") }
        shouldThrow<IllegalArgumentException> { CodeReference.Label("with space") }
        shouldThrow<IllegalArgumentException> { ImmediateOperand.SymbolAddress("_bad") }
        AddressBase.Symbol("main.items").name shouldBe "main.items"
    }

    test("memory references can be mapped to other registers") {
        val ref = IRMemory.indexed("main.items", 3, IRDataType.WORD, scale = 2, displacement = 4)
        val mapped = ref.mapRegisters { VirtualRegister.int(7) }
        (mapped as MemoryReference.Indexed).index.registerNumber shouldBe 7
        mapped.scale shouldBe 2
        mapped.displacement shouldBe 4
    }

    test("structured views of a register instruction") {
        val add = IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, destination = 1, source = 2)
        add.dest shouldBe RegisterOperand(
            VirtualRegister.IntReg(RegisterNum(1)),
            IRDataType.WORD,
            OperandRole.RESULT,
            OperandDirection.USE_DEF
        )
        add.srcA shouldBe RegisterOperand(
            VirtualRegister.IntReg(RegisterNum(2)),
            IRDataType.WORD,
            OperandRole.RIGHT,
            OperandDirection.USE
        )
        add.uses shouldBe setOf(
            VirtualRegister.IntReg(RegisterNum(1)),
            VirtualRegister.IntReg(RegisterNum(2))
        )
        add.definitions shouldBe setOf(VirtualRegister.IntReg(RegisterNum(1)))
    }

    test("structured views of a float instruction") {
        val sin = IRInstructions.binary(Opcode.FSIN, IRDataType.FLOAT, destination = 1, source = 2)
        sin.requireFloatDest().register shouldBe VirtualRegister.float(1)
        sin.requireFloatSourceA().register shouldBe VirtualRegister.float(2)
        sin.definitions shouldBe setOf(VirtualRegister.FloatReg(RegisterNum(1)))
        sin.uses shouldBe setOf(VirtualRegister.FloatReg(RegisterNum(2)))
    }

    test("structured views of an indexed memory instruction") {
        val loadx = IRInstructions.loadMemory(
            Opcode.LOADX, IRDataType.BYTE, 1,
            IRMemory.indexed("main.items", 2, IRDataType.WORD, scale = 3, displacement = 4)
        )
        val memory = loadx.requireMemory() as MemoryReference.Indexed
        memory.symbolName shouldBe "main.items"
        memory.scale shouldBe 3
        memory.displacement shouldBe 4
        loadx.uses shouldBe setOf(VirtualRegister.int(2))
        loadx.definitions shouldBe setOf(VirtualRegister.int(1))
        loadx.memoryEffect shouldBe MemoryEffect.READ
    }

    test("structured views of a hardware slot instruction") {
        val loadhr = IRInstructions.hardwareLoad(IRDataType.WORD, 5, CallingConventionSlot(10))
        loadhr.requireHardwareSlot() shouldBe HardwareSlotOperand(CallingConventionSlot(10), IRDataType.WORD)
        loadhr.definitions shouldBe setOf(VirtualRegister.int(5))
    }

    test("structured views of a branch instruction") {
        val branch = IRInstructions.branchImmediate(Opcode.BGT, IRDataType.BYTE, 3, 42, codeLabel("main.label"))
        branch.requireTarget() shouldBe CodeReference.Label("main.label")
        branch.requireImmediateInt() shouldBe 42
        branch.uses shouldBe setOf(VirtualRegister.int(3))
        branch.definitions shouldBe emptySet()
        branch.controlFlow shouldBe ControlFlowEffect.BRANCH
    }

    test("register mapping only changes the selected register") {
        val instr = IRInstructions.loadMemory(
            Opcode.LOADX, IRDataType.BYTE, 1,
            IRMemory.indexed("main.items", 2, IRDataType.WORD, scale = 3, displacement = 4)
        )
        val mapped = instr.mapRegisters { if (it == VirtualRegister.int(2)) VirtualRegister.int(9) else it }
        mapped.requireDest().registerNumber shouldBe 1
        (mapped.requireMemory() as MemoryReference.Indexed).index.registerNumber shouldBe 9
        (mapped.requireMemory() as MemoryReference.Indexed).scale shouldBe 3
    }

    test("destination register may also be a source register, the IR is not SSA") {
        // registers are freely reused: addr r1,r1 doubles r1 in place
        val add = IRInstructions.binary(Opcode.ADDR, IRDataType.BYTE, destination = 1, source = 1)
        add.definitions shouldBe setOf(VirtualRegister.int(1))
        add.uses shouldBe setOf(VirtualRegister.int(1))
        // LOADI may also reuse the pointer register
        IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, 1, IRMemory.indirect(1, 8)).uses shouldBe
                setOf(VirtualRegister.int(1))
        // but an instruction with two destinations must keep them distinct
        shouldThrow<IllegalArgumentException> {
            IRInstructions.divmodRegister(Opcode.DIVMODR, IRDataType.BYTE, quotient = 1, remainder = 1)
        }
    }
})
