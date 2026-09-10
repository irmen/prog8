package prog8.intermediate

/*
Typed construction helpers for IR instructions.

All IR producers (the IR code generator, the optimizers, the text codec and the tests) build
instructions through these factories. The factories derive the role, direction, register file and
data type of every operand from the OpcodeSchema, so a producer only has to supply the
register numbers and values.
*/


/** builders for memory references */
object IRMemory {
    fun direct(symbol: String, displacement: Int = 0): MemoryReference =
        MemoryReference.Direct(AddressBase.Symbol(symbol), displacement)

    fun direct(address: MemoryAddress, displacement: Int = 0): MemoryReference =
        MemoryReference.Direct(AddressBase.Absolute(address), displacement)

    fun direct(address: UInt, displacement: Int = 0): MemoryReference =
        MemoryReference.Direct(AddressBase.Absolute(MemoryAddress(address)), displacement)

    fun indexed(
        base: AddressBase,
        indexRegister: Int,
        indexType: IRDataType,
        scale: Int = 1,
        displacement: Int = 0
    ): MemoryReference =
        MemoryReference.Indexed(base, indexOperand(indexRegister, indexType), scale, displacement)

    fun indexed(
        symbol: String,
        indexRegister: Int,
        indexType: IRDataType,
        scale: Int = 1,
        displacement: Int = 0
    ): MemoryReference = indexed(AddressBase.Symbol(symbol), indexRegister, indexType, scale, displacement)

    fun indexed(
        address: UInt,
        indexRegister: Int,
        indexType: IRDataType,
        scale: Int = 1,
        displacement: Int = 0
    ): MemoryReference = indexed(AddressBase.Absolute(MemoryAddress(address)), indexRegister, indexType, scale, displacement)

    fun indirect(pointerRegister: Int, displacement: Int = 0, pointerType: IRDataType = IRDataType.POINTER): MemoryReference =
        MemoryReference.Indirect(pointerOperand(pointerRegister, pointerType), displacement)

    fun indexOperand(register: Int, type: IRDataType): RegisterOperand =
        RegisterOperand(VirtualRegister.int(register), type, OperandRole.INDEX, OperandDirection.USE, AllocationHint.PREFER_DATA)

    fun pointerOperand(register: Int, type: IRDataType = IRDataType.POINTER): RegisterOperand =
        RegisterOperand(VirtualRegister.int(register), type, OperandRole.POINTER_BASE, OperandDirection.USE, AllocationHint.PREFER_ADDRESS)
}


object IRInstructions {

    // ---- generic slot construction ----

    /** build the register operand for a slot of the given opcode, according to its schema */
    fun operandFor(
        opcode: Opcode,
        type: IRDataType?,
        slot: InstructionSlot,
        registerNumber: Int,
        registerType: IRDataType? = null
    ): RegisterOperand {
        val schema = OpcodeSchemas.get(opcode, type)
        val slotSchema = schema.registerSlot(slot)
            ?: throw IllegalArgumentException("$opcode has no register operand in slot $slot")
        val operandType = registerType ?: schema.typeFor(slotSchema.typeRule)
            ?: throw IllegalArgumentException("$opcode: the type of slot $slot must be given explicitly")
        val register = if (slotSchema.registerFile == RegisterFile.FLOAT)
            VirtualRegister.float(registerNumber) else VirtualRegister.int(registerNumber)
        return RegisterOperand(register, operandType, slotSchema.role, slotSchema.direction)
    }

    /** the immediate operand for the given opcode, with the data type the schema prescribes */
    fun immediateFor(opcode: Opcode, type: IRDataType?, value: Int): ImmediateOperand {
        val schema = OpcodeSchemas.get(opcode, type)
        val immSchema = schema.immediateSlot ?: throw IllegalArgumentException("$opcode has no immediate operand")
        val immType = schema.typeFor(immSchema.typeRule) ?: IRDataType.WORD
        return ImmediateOperand.Integer(value, immType)
    }

    // ---- typed factories ----

    /** an instruction without operands (NOP, RETURN, CLC, SEC, PUSHST, ...) */
    fun simple(opcode: Opcode, type: IRDataType? = null) = IRInstruction(opcode, type)

    fun load(destination: RegisterOperand, immediate: ImmediateOperand) =
        IRInstruction(Opcode.LOAD, destination.type, dest = destination, immediate = immediate)

    fun load(type: IRDataType, destination: Int, value: Int) =
        IRInstruction(
            Opcode.LOAD, type,
            dest = operandFor(Opcode.LOAD, type, InstructionSlot.DEST, destination),
            immediate = ImmediateOperand.Integer(value, type)
        )

    fun loadFloat(destination: Int, value: Double) =
        IRInstruction(
            Opcode.LOAD, IRDataType.FLOAT,
            dest = operandFor(Opcode.LOAD, IRDataType.FLOAT, InstructionSlot.DEST, destination),
            immediate = ImmediateOperand.FloatValue(value)
        )

    /** load the ADDRESS of a symbol into a register */
    fun loadAddress(type: IRDataType, destination: Int, symbol: String, offset: Int = 0) =
        IRInstruction(
            Opcode.LOAD, type,
            dest = operandFor(Opcode.LOAD, type, InstructionSlot.DEST, destination),
            immediate = ImmediateOperand.SymbolAddress(symbol, offset, if (type == IRDataType.FLOAT) IRDataType.POINTER else type)
        )

    fun loadMemory(destination: RegisterOperand, memory: MemoryReference): IRInstruction {
        val opcode = when (memory) {
            is MemoryReference.Direct -> Opcode.LOADM
            is MemoryReference.Indexed -> Opcode.LOADX
            is MemoryReference.Indirect -> Opcode.LOADI
        }
        return IRInstruction(opcode, destination.type, dest = destination, memory = memory)
    }

    fun loadMemory(opcode: Opcode, type: IRDataType, destination: Int, memory: MemoryReference) =
        IRInstruction(
            opcode, type,
            dest = operandFor(opcode, type, InstructionSlot.DEST, destination),
            memory = memory
        )

    fun storeMemory(opcode: Opcode, type: IRDataType, source: Int, memory: MemoryReference) =
        IRInstruction(
            opcode, type,
            srcA = operandFor(opcode, type, InstructionSlot.SRC_A, source),
            memory = memory
        )

    fun storeMemory(source: RegisterOperand, memory: MemoryReference): IRInstruction {
        val opcode = when (memory) {
            is MemoryReference.Direct -> Opcode.STOREM
            is MemoryReference.Indexed -> Opcode.STOREX
            is MemoryReference.Indirect -> Opcode.STOREI
        }
        return IRInstruction(opcode, source.type, srcA = source, memory = memory)
    }

    /** STOREZM / STOREZI / STOREZX: store a zero */
    fun storeZero(opcode: Opcode, type: IRDataType, memory: MemoryReference) =
        IRInstruction(opcode, type, memory = memory)

    /** STOREIM: store an immediate value into memory */
    fun storeImmediate(type: IRDataType, value: Int, memory: MemoryReference) =
        IRInstruction(Opcode.STOREIM, type, immediate = ImmediateOperand.Integer(value, type), memory = memory)

    fun storeImmediateFloat(value: Double, memory: MemoryReference) =
        IRInstruction(Opcode.STOREIM, IRDataType.FLOAT, immediate = ImmediateOperand.FloatValue(value), memory = memory)

    fun move(destination: RegisterOperand, source: RegisterOperand) =
        IRInstruction(Opcode.LOADR, destination.type, dest = destination, srcA = source)

    fun move(type: IRDataType, destination: Int, source: Int) =
        IRInstruction(
            Opcode.LOADR, type,
            dest = operandFor(Opcode.LOADR, type, InstructionSlot.DEST, destination),
            srcA = operandFor(Opcode.LOADR, type, InstructionSlot.SRC_A, source)
        )

    /** an operation with a single register operand: INC, DEC, NEG, INV, ASR, LSL, ROL, ... */
    fun unary(opcode: Opcode, type: IRDataType, destination: Int) =
        IRInstruction(opcode, type, dest = operandFor(opcode, type, InstructionSlot.DEST, destination))

    /** an operation with a destination register and a source register: ADDR, SUBR, EXT, SGN, FTOUB, ... */
    fun binary(opcode: Opcode, destination: RegisterOperand, source: RegisterOperand): IRInstruction {
        val type = OpcodeSchemas.typesFor(opcode)
            .filterNotNull()
            .filter { candidate ->
                val schema = OpcodeSchemas.get(opcode, candidate)
                schema.matches(InstructionSlot.DEST, destination) &&
                        schema.matches(InstructionSlot.SRC_A, source)
            }
            .singleOrNull()
            ?: throw IllegalArgumentException("$opcode: cannot infer instruction type from $destination and $source")
        return IRInstruction(opcode, type, dest = destination, srcA = source)
    }

    fun binary(opcode: Opcode, type: IRDataType, destination: Int, source: Int, sourceType: IRDataType? = null) =
        IRInstruction(
            opcode, type,
            dest = operandFor(opcode, type, InstructionSlot.DEST, destination),
            srcA = operandFor(opcode, type, InstructionSlot.SRC_A, source, sourceType)
        )

    /** an operation with a destination register and an immediate value: ADD, SUB, AND, ASRI, BITSET, ... */
    fun binaryImmediate(opcode: Opcode, type: IRDataType, destination: Int, value: Int) =
        IRInstruction(
            opcode, type,
            dest = operandFor(opcode, type, InstructionSlot.DEST, destination),
            immediate = immediateFor(opcode, type, value)
        )

    fun binaryImmediate(opcode: Opcode, destination: RegisterOperand, immediate: ImmediateOperand) =
        IRInstruction(opcode, destination.type, dest = destination, immediate = immediate)

    fun binaryImmediateFloat(opcode: Opcode, destination: Int, value: Double) =
        IRInstruction(
            opcode, IRDataType.FLOAT,
            dest = operandFor(opcode, IRDataType.FLOAT, InstructionSlot.DEST, destination),
            immediate = ImmediateOperand.FloatValue(value)
        )

    /** an operation on memory with an optional register or immediate operand: INCM, ADDM, ADDIM, LSLM, ... */
    fun memoryOp(opcode: Opcode, type: IRDataType, memory: MemoryReference, source: Int? = null) =
        IRInstruction(
            opcode, type,
            srcA = source?.let { operandFor(opcode, type, InstructionSlot.SRC_A, it) },
            memory = memory
        )

    fun memoryOpImmediate(opcode: Opcode, type: IRDataType, memory: MemoryReference, value: Int) =
        IRInstruction(opcode, type, immediate = immediateFor(opcode, type, value), memory = memory)

    fun memoryOpImmediateFloat(opcode: Opcode, memory: MemoryReference, value: Double) =
        IRInstruction(opcode, IRDataType.FLOAT, immediate = ImmediateOperand.FloatValue(value), memory = memory)

    /** CMP: compare two registers */
    fun compare(type: IRDataType, left: Int, right: Int) =
        IRInstruction(
            Opcode.CMP, type,
            srcA = operandFor(Opcode.CMP, type, InstructionSlot.SRC_A, left),
            srcB = operandFor(Opcode.CMP, type, InstructionSlot.SRC_B, right)
        )

    /** CMPI: compare a register with an immediate value */
    fun compareImmediate(type: IRDataType, left: Int, value: Int) =
        IRInstruction(
            Opcode.CMPI, type,
            srcA = operandFor(Opcode.CMPI, type, InstructionSlot.SRC_A, left),
            immediate = ImmediateOperand.Integer(value, type)
        )

    /** DIVMODR / SDIVMODR: division with quotient and remainder in two registers */
    fun divmodRegister(opcode: Opcode, type: IRDataType, quotient: Int, remainder: Int) =
        IRInstruction(
            opcode, type,
            dest = operandFor(opcode, type, InstructionSlot.DEST, quotient),
            destB = operandFor(opcode, type, InstructionSlot.DEST_B, remainder)
        )

    /** DIVMOD / SDIVMOD: division by an immediate value, with quotient and remainder in two registers */
    fun divmodImmediate(opcode: Opcode, type: IRDataType, quotient: Int, remainder: Int, value: Int) =
        IRInstruction(
            opcode, type,
            dest = operandFor(opcode, type, InstructionSlot.DEST, quotient),
            destB = operandFor(opcode, type, InstructionSlot.DEST_B, remainder),
            immediate = ImmediateOperand.Integer(value, type)
        )

    /** CONCAT: combine two registers into a wider one */
    fun concat(type: IRDataType, destination: Int, msb: Int, lsb: Int) =
        IRInstruction(
            Opcode.CONCAT, type,
            dest = operandFor(Opcode.CONCAT, type, InstructionSlot.DEST, destination),
            srcA = operandFor(Opcode.CONCAT, type, InstructionSlot.SRC_A, msb),
            srcB = operandFor(Opcode.CONCAT, type, InstructionSlot.SRC_B, lsb)
        )

    /** FCOMP: compare two float registers, result in an integer register */
    fun floatCompare(destination: Int, left: Int, right: Int) =
        IRInstruction(
            Opcode.FCOMP, IRDataType.FLOAT,
            dest = operandFor(Opcode.FCOMP, IRDataType.FLOAT, InstructionSlot.DEST, destination),
            srcA = operandFor(Opcode.FCOMP, IRDataType.FLOAT, InstructionSlot.SRC_A, left),
            srcB = operandFor(Opcode.FCOMP, IRDataType.FLOAT, InstructionSlot.SRC_B, right)
        )

    fun jump(target: CodeReference) =
        if (target is CodeReference.Indirect) IRInstruction(Opcode.JUMPI, target = target)
        else IRInstruction(Opcode.JUMP, target = target)

    fun jumpIndirect(pointerRegister: Int, pointerType: IRDataType = IRDataType.POINTER) =
        IRInstruction(Opcode.JUMPI, target = codeIndirect(pointerRegister, pointerType))

    fun branch(
        opcode: Opcode,
        target: CodeReference,
        srcA: RegisterOperand? = null,
        srcB: RegisterOperand? = null,
        immediate: ImmediateOperand? = null
    ): IRInstruction {
        val type = srcA?.type
        return IRInstruction(opcode, type, srcA = srcA, srcB = srcB, immediate = immediate, target = target)
    }

    /** a status-bit branch (BSTEQ, BSTCC, ...) */
    fun branch(opcode: Opcode, target: CodeReference) = IRInstruction(opcode, target = target)

    /** a comparison branch against a register (BGTR, BGESR, ...) */
    fun branchRegister(opcode: Opcode, type: IRDataType, left: Int, right: Int, target: CodeReference) =
        IRInstruction(
            opcode, type,
            srcA = operandFor(opcode, type, InstructionSlot.SRC_A, left),
            srcB = operandFor(opcode, type, InstructionSlot.SRC_B, right),
            target = target
        )

    /** a comparison branch against an immediate value (BGT, BLE, ...) */
    fun branchImmediate(opcode: Opcode, type: IRDataType, left: Int, value: Int, target: CodeReference) =
        IRInstruction(
            opcode, type,
            srcA = operandFor(opcode, type, InstructionSlot.SRC_A, left),
            immediate = ImmediateOperand.Integer(value, type),
            target = target
        )

    fun call(opcode: Opcode, site: CallSite) = IRInstruction(opcode, callSite = site)

    fun call(site: CallSite) = IRInstruction(Opcode.CALL, callSite = site)

    fun syscall(number: Int, arguments: List<CallArgument> = emptyList(), results: List<CallResult> = emptyList()) =
        IRInstruction(Opcode.SYSCALL, callSite = CallSite(CallTarget.SystemCall(number), arguments, results))

    fun returnVoid() = IRInstruction(Opcode.RETURN)

    fun returnRegister(type: IRDataType, source: Int) =
        IRInstruction(Opcode.RETURNR, type, srcA = operandFor(Opcode.RETURNR, type, InstructionSlot.SRC_A, source))

    fun returnImmediate(type: IRDataType, value: Int) =
        IRInstruction(Opcode.RETURNI, type, immediate = ImmediateOperand.Integer(value, type))

    fun returnImmediateFloat(value: Double) =
        IRInstruction(Opcode.RETURNI, IRDataType.FLOAT, immediate = ImmediateOperand.FloatValue(value))

    fun push(type: IRDataType, source: Int) =
        IRInstruction(Opcode.PUSH, type, srcA = operandFor(Opcode.PUSH, type, InstructionSlot.SRC_A, source))

    fun pop(type: IRDataType, destination: Int) =
        IRInstruction(Opcode.POP, type, dest = operandFor(Opcode.POP, type, InstructionSlot.DEST, destination))

    /** LOADHR: load a cpu hardware register (calling convention slot) into a virtual register */
    fun hardwareLoad(type: IRDataType, destination: Int, slot: CallingConventionSlot) =
        IRInstruction(
            Opcode.LOADHR, type,
            dest = operandFor(Opcode.LOADHR, type, InstructionSlot.DEST, destination),
            hardwareSlot = HardwareSlotOperand(slot, type)
        )

    /** STOREHR: store a virtual register into a cpu hardware register (calling convention slot) */
    fun hardwareStore(type: IRDataType, source: Int, slot: CallingConventionSlot) =
        IRInstruction(
            Opcode.STOREHR, type,
            srcA = operandFor(Opcode.STOREHR, type, InstructionSlot.SRC_A, source),
            hardwareSlot = HardwareSlotOperand(slot, type)
        )

    fun align(value: Int) =
        IRInstruction(Opcode.ALIGN, immediate = ImmediateOperand.Integer(value, IRDataType.LONG))
}

private fun OpcodeSchema.matches(slot: InstructionSlot, operand: RegisterOperand): Boolean {
    val slotSchema = registerSlot(slot) ?: return false
    if ((slotSchema.registerFile == RegisterFile.FLOAT) != operand.isFloat)
        return false
    return when (val rule = slotSchema.typeRule) {
        TypeRule.InstructionType -> operand.type == type
        TypeRule.IndexType, TypeRule.PointerType -> operand.type != IRDataType.FLOAT
        is TypeRule.Fixed -> operand.type == rule.type
    }
}


// ---- convenient code reference builders ----

fun codeLabel(name: String, offset: Int = 0): CodeReference = CodeReference.Label(name, offset)

fun codeAddress(address: UInt): CodeReference = CodeReference.Absolute(MemoryAddress(address))

fun codeAddress(address: MemoryAddress): CodeReference = CodeReference.Absolute(address)

fun codeIndirect(pointerRegister: Int, pointerType: IRDataType = IRDataType.POINTER): CodeReference =
    CodeReference.Indirect(
        RegisterOperand(
            VirtualRegister.int(pointerRegister), pointerType,
            OperandRole.INDIRECT_TARGET, OperandDirection.USE, AllocationHint.PREFER_ADDRESS
        )
    )


// ---- typed operand extraction helpers, for the IR consumers ----

fun IRInstruction.requireDest(): RegisterOperand =
    dest ?: throw IllegalArgumentException("$opcode: missing destination operand")

fun IRInstruction.requireDestB(): RegisterOperand =
    destB ?: throw IllegalArgumentException("$opcode: missing secondary destination operand")

fun IRInstruction.requireSrcA(): RegisterOperand =
    srcA ?: throw IllegalArgumentException("$opcode: missing first source operand")

fun IRInstruction.requireSrcB(): RegisterOperand =
    srcB ?: throw IllegalArgumentException("$opcode: missing second source operand")

fun IRInstruction.requireIntDest(): RegisterOperand = requireDest().also {
    require(!it.isFloat) { "$opcode: destination must be an integer register" }
}

fun IRInstruction.requireFloatDest(): RegisterOperand = requireDest().also {
    require(it.isFloat) { "$opcode: destination must be a float register" }
}

fun IRInstruction.requireIntSourceA(): RegisterOperand = requireSrcA().also {
    require(!it.isFloat) { "$opcode: source must be an integer register" }
}

fun IRInstruction.requireFloatSourceA(): RegisterOperand = requireSrcA().also {
    require(it.isFloat) { "$opcode: source must be a float register" }
}

fun IRInstruction.requireImmediate(): ImmediateOperand =
    immediate ?: throw IllegalArgumentException("$opcode: missing immediate operand")

fun IRInstruction.requireImmediateInt(): Int =
    (immediate as? ImmediateOperand.Integer)?.value
        ?: throw IllegalArgumentException("$opcode: missing integer immediate operand")

fun IRInstruction.requireImmediateFloat(): Double =
    (immediate as? ImmediateOperand.FloatValue)?.value
        ?: throw IllegalArgumentException("$opcode: missing float immediate operand")

fun IRInstruction.requireMemory(): MemoryReference =
    memory ?: throw IllegalArgumentException("$opcode: missing memory operand")

fun IRInstruction.requireTarget(): CodeReference =
    target ?: throw IllegalArgumentException("$opcode: missing code target operand")

fun IRInstruction.requireCallSite(): CallSite =
    callSite ?: throw IllegalArgumentException("$opcode: missing call site operand")

fun IRInstruction.requireHardwareSlot(): HardwareSlotOperand =
    hardwareSlot ?: throw IllegalArgumentException("$opcode: missing hardware slot operand")

/** the number of an integer register operand */
val RegisterOperand.intNumber: Int
    get() = (register as? VirtualRegister.IntReg)?.number?.value
        ?: throw IllegalArgumentException("not an integer register: $register")

/** the number of a float register operand */
val RegisterOperand.floatNumber: RegisterNum
    get() = (register as? VirtualRegister.FloatReg)?.number
        ?: throw IllegalArgumentException("not a float register: $register")
