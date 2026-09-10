import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import prog8.intermediate.*


class TestOpcodeSchema: FunSpec({

    test("every opcode has at least one schema") {
        Opcode.entries.forEach { opcode ->
            OpcodeSchemas.schemasFor(opcode).shouldNotBeEmpty()
        }
        OpcodeSchemas.coveredOpcodes shouldBe Opcode.entries.toSet()
    }

    test("there is exactly one schema per opcode and type") {
        OpcodeSchemas.all().groupBy { it.opcode to it.type }.values.all { it.size == 1 } shouldBe true
    }

    test("register-register arithmetic shape") {
        OpcodeSchemas.get(Opcode.ADDR, IRDataType.WORD).slots shouldBe listOf(
            RegisterSlotSchema(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF,
                RegisterFile.INTEGER, TypeRule.InstructionType),
            RegisterSlotSchema(InstructionSlot.SRC_A, OperandRole.RIGHT, OperandDirection.USE,
                RegisterFile.INTEGER, TypeRule.InstructionType)
        )
        OpcodeSchemas.get(Opcode.ADDR, IRDataType.FLOAT).registerSlots.map { it.registerFile } shouldBe
                listOf(RegisterFile.FLOAT, RegisterFile.FLOAT)
    }

    test("two-result division shape") {
        OpcodeSchemas.get(Opcode.DIVMOD, IRDataType.WORD).slots.map { it.slot } shouldBe
                listOf(InstructionSlot.DEST, InstructionSlot.DEST_B, InstructionSlot.IMMEDIATE)
        OpcodeSchemas.get(Opcode.SDIVMOD, IRDataType.WORD).registerSlots.map { it.role } shouldBe
                listOf(OperandRole.RESULT, OperandRole.SECONDARY_RESULT)
        OpcodeSchemas.get(Opcode.DIVMODR, IRDataType.BYTE).slots.map { it.slot } shouldBe
                listOf(InstructionSlot.DEST, InstructionSlot.DEST_B)
    }

    test("float indexed load has an integer index register") {
        val schema = OpcodeSchemas.get(Opcode.LOADX, IRDataType.FLOAT)
        schema.slots.map { it.slot } shouldBe listOf(InstructionSlot.DEST, InstructionSlot.MEMORY)
        schema.registerSlot(InstructionSlot.DEST)!!.registerFile shouldBe RegisterFile.FLOAT
        schema.memorySlot!!.kind shouldBe MemoryKind.INDEXED
        schema.memoryEffect shouldBe MemoryEffect.READ
    }

    test("pointer dereference uses an indirect memory reference") {
        val schema = OpcodeSchemas.get(Opcode.LOADI, IRDataType.BYTE)
        schema.slots.map { it.slot } shouldBe listOf(InstructionSlot.DEST, InstructionSlot.MEMORY)
        schema.memorySlot!!.kind shouldBe MemoryKind.INDIRECT
        OpcodeSchemas.get(Opcode.STOREI, IRDataType.BYTE).memorySlot!!.kind shouldBe MemoryKind.INDIRECT
        OpcodeSchemas.get(Opcode.STOREZI, IRDataType.BYTE).slots.map { it.slot } shouldBe listOf(InstructionSlot.MEMORY)
    }

    test("hardware register transfer uses a hardware slot") {
        OpcodeSchemas.get(Opcode.LOADHR, IRDataType.WORD).slots.map { it.slot } shouldBe
                listOf(InstructionSlot.DEST, InstructionSlot.HARDWARE_SLOT)
        OpcodeSchemas.get(Opcode.STOREHR, IRDataType.WORD).slots.map { it.slot } shouldBe
                listOf(InstructionSlot.SRC_A, InstructionSlot.HARDWARE_SLOT)
    }

    test("hardware slots must have the instruction type") {
        shouldThrow<IllegalArgumentException> {
            IRInstruction(
                Opcode.LOADHR,
                IRDataType.BYTE,
                dest = IRInstructions.operandFor(Opcode.LOADHR, IRDataType.BYTE, InstructionSlot.DEST, 1),
                hardwareSlot = HardwareSlotOperand(CallingConventionSlot(0), IRDataType.WORD)
            )
        }
    }

    test("concat combines two registers into a wider one") {
        val schema = OpcodeSchemas.get(Opcode.CONCAT, IRDataType.BYTE)
        schema.slots.map { it.slot } shouldBe listOf(InstructionSlot.DEST, InstructionSlot.SRC_A, InstructionSlot.SRC_B)
        schema.registerSlot(InstructionSlot.DEST)!!.typeRule shouldBe TypeRule.Fixed(IRDataType.WORD)
        OpcodeSchemas.get(Opcode.CONCAT, IRDataType.WORD).registerSlot(InstructionSlot.DEST)!!.typeRule shouldBe
                TypeRule.Fixed(IRDataType.LONG)
    }

    test("branches and jumps have a code target") {
        OpcodeSchemas.get(Opcode.JUMP, null).targetSlot!!.kind shouldBe TargetKind.STATIC
        OpcodeSchemas.get(Opcode.JUMPI, null).targetSlot!!.kind shouldBe TargetKind.INDIRECT
        OpcodeSchemas.get(Opcode.BSTEQ, null).slots.map { it.slot } shouldBe listOf(InstructionSlot.TARGET)
        OpcodeSchemas.get(Opcode.BSTEQ, null).statusEffect shouldBe StatusEffect.READS
        OpcodeSchemas.get(Opcode.BGT, IRDataType.BYTE).slots.map { it.slot } shouldBe
                listOf(InstructionSlot.SRC_A, InstructionSlot.IMMEDIATE, InstructionSlot.TARGET)
        OpcodeSchemas.get(Opcode.BGTR, IRDataType.BYTE).slots.map { it.slot } shouldBe
                listOf(InstructionSlot.SRC_A, InstructionSlot.SRC_B, InstructionSlot.TARGET)
        OpcodeSchemas.get(Opcode.JUMP, null).controlFlow shouldBe ControlFlowEffect.JUMP
    }

    test("returns") {
        OpcodeSchemas.get(Opcode.RETURN, null).slots shouldBe emptyList()
        OpcodeSchemas.get(Opcode.RETURNR, IRDataType.FLOAT).registerSlot(InstructionSlot.SRC_A)!!.registerFile shouldBe RegisterFile.FLOAT
        OpcodeSchemas.get(Opcode.RETURNI, IRDataType.BYTE).slots.map { it.slot } shouldBe listOf(InstructionSlot.IMMEDIATE)
        OpcodeSchemas.get(Opcode.RETURN, null).controlFlow shouldBe ControlFlowEffect.RETURN
    }

    test("all call opcodes only have a call site operand") {
        val callKinds = mapOf(
            Opcode.CALL to CallKind.NORMAL,
            Opcode.CALLI to CallKind.INDIRECT,
            Opcode.CALLFAR to CallKind.FAR,
            Opcode.CALLFARVB to CallKind.FAR_VARBANK,
            Opcode.SYSCALL to CallKind.SYSCALL
        )
        for((opcode, kind) in callKinds) {
            val schema = OpcodeSchemas.get(opcode, null)
            schema.slots.map { it.slot } shouldBe listOf(InstructionSlot.CALL_SITE)
            schema.callSlot!!.kind shouldBe kind
            schema.controlFlow shouldBe ControlFlowEffect.CALL
        }
    }

    test("memory and status effects are declared") {
        OpcodeSchemas.get(Opcode.LOADM, IRDataType.BYTE).memoryEffect shouldBe MemoryEffect.READ
        OpcodeSchemas.get(Opcode.STOREM, IRDataType.BYTE).memoryEffect shouldBe MemoryEffect.WRITE
        OpcodeSchemas.get(Opcode.INCM, IRDataType.BYTE).memoryEffect shouldBe MemoryEffect.READ_WRITE
        OpcodeSchemas.get(Opcode.ADDR, IRDataType.BYTE).memoryEffect shouldBe MemoryEffect.NONE
        OpcodeSchemas.get(Opcode.CMP, IRDataType.BYTE).statusEffect shouldBe StatusEffect.SETS
        OpcodeSchemas.get(Opcode.ROXL, IRDataType.BYTE).statusEffect shouldBe StatusEffect.READS_AND_SETS
        OpcodeSchemas.get(Opcode.LOADM, IRDataType.BYTE).statusEffect shouldBe StatusEffect.NONE
    }

    test("word schemas also cover the pointer type") {
        OpcodeSchemas.typesFor(Opcode.LOADM) shouldBe setOf(
            IRDataType.BYTE, IRDataType.WORD, IRDataType.POINTER, IRDataType.LONG, IRDataType.FLOAT
        )
        OpcodeSchemas.typesFor(Opcode.NOP) shouldBe setOf(null)
    }

    test("every schema is internally consistent") {
        OpcodeSchemas.all().forEach { schema ->
            // at most one operand per slot kind
            schema.slots.groupBy { it.slot }.values.all { it.size == 1 } shouldBe true
            // float registers only in float instructions
            schema.registerSlots.forEach { slot ->
                if(slot.registerFile == RegisterFile.FLOAT)
                    schema.type shouldBe IRDataType.FLOAT
            }
            // canonical print order: destinations come before sources
            val slotOrder = schema.slots.map { it.slot }
            val destIndex = slotOrder.indexOfFirst { it == InstructionSlot.DEST }
            val srcIndex = slotOrder.indexOfFirst { it == InstructionSlot.SRC_A }
            if(destIndex >= 0 && srcIndex >= 0)
                (destIndex < srcIndex) shouldBe true
        }
    }
})
