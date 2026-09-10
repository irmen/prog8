package prog8.intermediate

/*
Opcode schema: the single source of truth for the shape and semantics of every IR instruction.

For every (opcode, datatype) combination there is exactly one OpcodeSchema that declares:
 - which operand slots the instruction has, in canonical (print) order
 - for register slots: the register file (integer/float), the semantic role, the direction
   (use/def/use+def) and how the register's data type is derived
 - what the instruction does to memory, to the status flags, and to the control flow
*/


enum class InstructionSlot {
    DEST, DEST_B, SRC_A, SRC_B, IMMEDIATE, HARDWARE_SLOT, MEMORY, TARGET, CALL_SITE
}

enum class RegisterFile { INTEGER, FLOAT }

/** how the data type of an operand is determined */
sealed interface TypeRule {
    /** same data type as the instruction itself */
    data object InstructionType : TypeRule

    /** an array index register: any integer type (the width is target specific) */
    data object IndexType : TypeRule

    /** a pointer register: an integer type wide enough to hold an address */
    data object PointerType : TypeRule

    /** a fixed data type regardless of the instruction's type */
    data class Fixed(val type: IRDataType) : TypeRule
}

sealed interface SlotSchema {
    val slot: InstructionSlot
}

data class RegisterSlotSchema(
    override val slot: InstructionSlot,
    val role: OperandRole,
    val direction: OperandDirection,
    val registerFile: RegisterFile,
    val typeRule: TypeRule
) : SlotSchema

data class ImmediateSlotSchema(
    val role: OperandRole,
    val typeRule: TypeRule,
    val allowSymbolAddress: Boolean = false
) : SlotSchema {
    override val slot: InstructionSlot get() = InstructionSlot.IMMEDIATE
}

enum class MemoryKind { DIRECT, INDEXED, INDIRECT }

data class MemorySlotSchema(
    val kind: MemoryKind,
    val effect: MemoryEffect
) : SlotSchema {
    override val slot: InstructionSlot get() = InstructionSlot.MEMORY
}

data class HardwareSlotSchema(val typeRule: TypeRule = TypeRule.InstructionType) : SlotSchema {
    override val slot: InstructionSlot get() = InstructionSlot.HARDWARE_SLOT
}

enum class TargetKind { STATIC, INDIRECT }

data class TargetSlotSchema(val kind: TargetKind) : SlotSchema {
    override val slot: InstructionSlot get() = InstructionSlot.TARGET
}

enum class CallKind { NORMAL, INDIRECT, FAR, FAR_VARBANK, SYSCALL }

data class CallSiteSlotSchema(val kind: CallKind) : SlotSchema {
    override val slot: InstructionSlot get() = InstructionSlot.CALL_SITE
}


data class OpcodeSchema(
    val opcode: Opcode,
    val type: IRDataType?,
    val slots: List<SlotSchema>,
    val memoryEffect: MemoryEffect = MemoryEffect.NONE,
    val statusEffect: StatusEffect = StatusEffect.NONE,
    val controlFlow: ControlFlowEffect = ControlFlowEffect.FALLTHROUGH
) {
    val registerSlots: List<RegisterSlotSchema> = slots.filterIsInstance<RegisterSlotSchema>()
    val immediateSlot: ImmediateSlotSchema? = slots.filterIsInstance<ImmediateSlotSchema>().singleOrNull()
    val memorySlot: MemorySlotSchema? = slots.filterIsInstance<MemorySlotSchema>().singleOrNull()
    val hardwareSlotSchema: HardwareSlotSchema? = slots.filterIsInstance<HardwareSlotSchema>().singleOrNull()
    val targetSlot: TargetSlotSchema? = slots.filterIsInstance<TargetSlotSchema>().singleOrNull()
    val callSlot: CallSiteSlotSchema? = slots.filterIsInstance<CallSiteSlotSchema>().singleOrNull()

    fun registerSlot(slot: InstructionSlot): RegisterSlotSchema? = registerSlots.firstOrNull { it.slot == slot }

    /** the data type an operand in this slot must have */
    fun typeFor(rule: TypeRule): IRDataType? = when (rule) {
        is TypeRule.Fixed -> rule.type
        TypeRule.InstructionType -> type
        TypeRule.IndexType -> null          // target specific width, declared by the operand itself
        TypeRule.PointerType -> null        // target specific width, declared by the operand itself
    }
}


object OpcodeSchemas {

    private val schemas: Map<Opcode, Map<IRDataType?, OpcodeSchema>> = buildSchemas()

    fun schemasFor(opcode: Opcode): List<OpcodeSchema> = schemas[opcode]?.values?.toList() ?: emptyList()

    fun find(opcode: Opcode, type: IRDataType?): OpcodeSchema? = schemas[opcode]?.get(type)

    fun get(opcode: Opcode, type: IRDataType?): OpcodeSchema =
        find(opcode, type) ?: throw IllegalArgumentException("type $type invalid for $opcode")

    /** the schema for an opcode when the type is not (yet) known: the typeless one, or the byte one */
    fun defaultFor(opcode: Opcode): OpcodeSchema {
        val perType = schemas[opcode] ?: throw IllegalArgumentException("unknown opcode $opcode")
        return perType[null] ?: perType[IRDataType.BYTE] ?: perType.values.first()
    }

    fun typesFor(opcode: Opcode): Set<IRDataType?> = schemas[opcode]?.keys ?: emptySet()

    fun all(): List<OpcodeSchema> = schemas.values.flatMap { it.values }

    val coveredOpcodes: Set<Opcode> get() = schemas.keys

    /** does this opcode require a data type? */
    fun requiresType(opcode: Opcode): Boolean = schemas.getValue(opcode).keys.none { it == null }

    fun validate(instruction: IRInstruction) {
        val schema = get(instruction.opcode, instruction.type)

        fun checkRegister(slot: InstructionSlot, operand: RegisterOperand?) {
            val slotSchema = schema.registerSlot(slot)
            if (slotSchema == null) {
                require(operand == null) { "${instruction.opcode}: unexpected operand in slot $slot" }
                return
            }
            requireNotNull(operand) { "${instruction.opcode}: missing operand in slot $slot" }
            require(operand.role == slotSchema.role) {
                "${instruction.opcode}: operand in slot $slot must have role ${slotSchema.role}, got ${operand.role}"
            }
            require(operand.direction == slotSchema.direction) {
                "${instruction.opcode}: operand in slot $slot must have direction ${slotSchema.direction}, got ${operand.direction}"
            }
            when (slotSchema.registerFile) {
                RegisterFile.INTEGER -> require(!operand.isFloat) { "${instruction.opcode}: slot $slot needs an integer register" }
                RegisterFile.FLOAT -> require(operand.isFloat) { "${instruction.opcode}: slot $slot needs a float register" }
            }
            val requiredType = schema.typeFor(slotSchema.typeRule)
            if (requiredType != null)
                require(operand.type == requiredType) {
                    "${instruction.opcode}: slot $slot needs type $requiredType, got ${operand.type}"
                }
            else
                require(operand.type != IRDataType.FLOAT) { "${instruction.opcode}: slot $slot needs an integer type" }
        }

        checkRegister(InstructionSlot.DEST, instruction.dest)
        checkRegister(InstructionSlot.DEST_B, instruction.destB)
        checkRegister(InstructionSlot.SRC_A, instruction.srcA)
        checkRegister(InstructionSlot.SRC_B, instruction.srcB)

        val immediateSchema = schema.immediateSlot
        if (immediateSchema == null)
            require(instruction.immediate == null) { "${instruction.opcode}: unexpected immediate operand" }
        else {
            val immediate = requireNotNull(instruction.immediate) { "${instruction.opcode}: missing immediate operand" }
            if (immediate is ImmediateOperand.SymbolAddress)
                require(immediateSchema.allowSymbolAddress) { "${instruction.opcode}: symbol address not allowed here" }
            else {
                val requiredType = schema.typeFor(immediateSchema.typeRule)
                if (requiredType != null)
                    require(immediate.type == requiredType) {
                        "${instruction.opcode}: immediate needs type $requiredType, got ${immediate.type}"
                    }
            }
            if (immediate is ImmediateOperand.Integer) {
                when (immediate.type) {
                    IRDataType.BYTE -> require(immediate.value in -128..255) { "immediate value out of range for byte: ${immediate.value}" }
                    IRDataType.WORD -> require(immediate.value in -32768..65535) { "immediate value out of range for word: ${immediate.value}" }
                    else -> {}
                }
            }
        }

        val memorySchema = schema.memorySlot
        if (memorySchema == null)
            require(instruction.memory == null) { "${instruction.opcode}: unexpected memory operand" }
        else {
            val memory = requireNotNull(instruction.memory) { "${instruction.opcode}: missing memory operand" }
            val kind = when (memory) {
                is MemoryReference.Direct -> MemoryKind.DIRECT
                is MemoryReference.Indexed -> MemoryKind.INDEXED
                is MemoryReference.Indirect -> MemoryKind.INDIRECT
            }
            require(kind == memorySchema.kind) { "${instruction.opcode}: needs ${memorySchema.kind} memory reference, got $kind" }
        }

        val hardwareSchema = schema.hardwareSlotSchema
        if (hardwareSchema == null)
            require(instruction.hardwareSlot == null) { "${instruction.opcode}: unexpected hardware slot operand" }
        else {
            val hardwareSlot = requireNotNull(instruction.hardwareSlot) { "${instruction.opcode}: missing hardware slot operand" }
            val requiredType = schema.typeFor(hardwareSchema.typeRule)
            if (requiredType != null) {
                require(hardwareSlot.type == requiredType) {
                    "${instruction.opcode}: hardware slot needs type $requiredType, got ${hardwareSlot.type}"
                }
            }
        }

        val targetSchema = schema.targetSlot
        if (targetSchema == null)
            require(instruction.target == null) { "${instruction.opcode}: unexpected code target operand" }
        else {
            val target = requireNotNull(instruction.target) { "${instruction.opcode}: missing code target operand" }
            when (targetSchema.kind) {
                TargetKind.STATIC -> require(target !is CodeReference.Indirect) { "${instruction.opcode}: needs a static code target" }
                TargetKind.INDIRECT -> require(target is CodeReference.Indirect) { "${instruction.opcode}: needs an indirect code target" }
            }
        }

        val callSchema = schema.callSlot
        if (callSchema == null)
            require(instruction.callSite == null) { "${instruction.opcode}: unexpected call site operand" }
        else {
            val callSite = requireNotNull(instruction.callSite) { "${instruction.opcode}: missing call site operand" }
            val kind = when (val target = callSite.target) {
                is CallTarget.Direct -> if (target.reference is CodeReference.Indirect) CallKind.INDIRECT else CallKind.NORMAL
                is CallTarget.Banked -> CallKind.FAR
                is CallTarget.AmigaLibrary -> CallKind.FAR
                is CallTarget.BankedVariable -> CallKind.FAR_VARBANK
                is CallTarget.SystemCall -> CallKind.SYSCALL
            }
            require(kind == callSchema.kind) { "${instruction.opcode}: needs a ${callSchema.kind} call target, got $kind" }
        }

        // NOTE: the IR is not SSA, registers are freely reused. A defined register
        // may also occur as a used register in the same instruction (e.g. `addr r1,r1`,
        // `cmp r1,r1`, `loadx r2,[base+r2*2]`, or a call result reusing an argument
        // register), so no distinctness is enforced between USE and DEF operands.
        // The only exception is an instruction with two definitions: those must be
        // distinct registers, otherwise one result would clobber the other.
        if (instruction.dest != null && instruction.destB != null)
            require(instruction.dest.register != instruction.destB.register) {
                "${instruction.opcode}: the two destination registers must be distinct"
            }
    }


    private fun buildSchemas(): Map<Opcode, Map<IRDataType?, OpcodeSchema>> {
        val result = mutableMapOf<Opcode, MutableMap<IRDataType?, OpcodeSchema>>()

        fun expand(typespec: String): List<IRDataType?> =
            if (typespec == "N") listOf(null)
            else buildList {
                if ('B' in typespec) add(IRDataType.BYTE)
                if ('W' in typespec) { add(IRDataType.WORD); add(IRDataType.POINTER) }
                if ('L' in typespec) add(IRDataType.LONG)
                if ('F' in typespec) add(IRDataType.FLOAT)
            }

        fun define(
            opcode: Opcode,
            typespec: String,
            memoryEffect: MemoryEffect = MemoryEffect.NONE,
            statusEffect: StatusEffect = StatusEffect.NONE,
            controlFlow: ControlFlowEffect = ControlFlowEffect.FALLTHROUGH,
            slots: (IRDataType?) -> List<SlotSchema>
        ) {
            val perType = result.getOrPut(opcode) { mutableMapOf() }
            for (type in expand(typespec)) {
                require(type !in perType) { "duplicate schema for $opcode $type" }
                perType[type] = OpcodeSchema(opcode, type, slots(type), memoryEffect, statusEffect, controlFlow)
            }
        }

        // ---- slot construction helpers ----

        fun ireg(slot: InstructionSlot, role: OperandRole, direction: OperandDirection, rule: TypeRule = TypeRule.InstructionType) =
            RegisterSlotSchema(slot, role, direction, RegisterFile.INTEGER, rule)

        fun freg(slot: InstructionSlot, role: OperandRole, direction: OperandDirection) =
            RegisterSlotSchema(slot, role, direction, RegisterFile.FLOAT, TypeRule.Fixed(IRDataType.FLOAT))

        fun reg(file: RegisterFile, slot: InstructionSlot, role: OperandRole, direction: OperandDirection, rule: TypeRule = TypeRule.InstructionType) =
            if (file == RegisterFile.FLOAT) freg(slot, role, direction) else ireg(slot, role, direction, rule)

        fun fileOf(type: IRDataType?) = if (type == IRDataType.FLOAT) RegisterFile.FLOAT else RegisterFile.INTEGER

        fun imm(role: OperandRole = OperandRole.VALUE, rule: TypeRule = TypeRule.InstructionType, allowSymbol: Boolean = false) =
            ImmediateSlotSchema(role, rule, allowSymbol)

        fun mem(kind: MemoryKind, effect: MemoryEffect) = MemorySlotSchema(kind, effect)

        fun widened(type: IRDataType?): IRDataType = when (type) {
            IRDataType.BYTE -> IRDataType.WORD
            else -> IRDataType.LONG
        }

        // ---- load and store ----

        define(Opcode.NOP, "N") { emptyList() }

        define(Opcode.LOAD, "BWLF") { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                imm(OperandRole.VALUE, TypeRule.InstructionType, allowSymbol = true)
            )
        }
        define(Opcode.LOADM, "BWLF", memoryEffect = MemoryEffect.READ) { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                mem(MemoryKind.DIRECT, MemoryEffect.READ)
            )
        }
        define(Opcode.LOADX, "BWLF", memoryEffect = MemoryEffect.READ) { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                mem(MemoryKind.INDEXED, MemoryEffect.READ)
            )
        }
        define(Opcode.LOADR, "BWLF") { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)
            )
        }
        define(Opcode.LOADHR, "BWLF") { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                HardwareSlotSchema()
            )
        }
        define(Opcode.LOADI, "BWLF", memoryEffect = MemoryEffect.READ) { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                mem(MemoryKind.INDIRECT, MemoryEffect.READ)
            )
        }
        define(Opcode.LOADHFACZERO, "F") { listOf(freg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF)) }
        define(Opcode.LOADHFACONE, "F") { listOf(freg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF)) }

        define(Opcode.STOREM, "BWLF", memoryEffect = MemoryEffect.WRITE) { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE),
                mem(MemoryKind.DIRECT, MemoryEffect.WRITE)
            )
        }
        define(Opcode.STOREX, "BWLF", memoryEffect = MemoryEffect.WRITE) { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE),
                mem(MemoryKind.INDEXED, MemoryEffect.WRITE)
            )
        }
        define(Opcode.STOREZM, "BWLF", memoryEffect = MemoryEffect.WRITE) { listOf(mem(MemoryKind.DIRECT, MemoryEffect.WRITE)) }
        define(Opcode.STOREZI, "BWLF", memoryEffect = MemoryEffect.WRITE) { listOf(mem(MemoryKind.INDIRECT, MemoryEffect.WRITE)) }
        define(Opcode.STOREZX, "BWLF", memoryEffect = MemoryEffect.WRITE) { listOf(mem(MemoryKind.INDEXED, MemoryEffect.WRITE)) }
        define(Opcode.STOREIM, "BWLF", memoryEffect = MemoryEffect.WRITE) {
            listOf(
                imm(OperandRole.VALUE, TypeRule.InstructionType),
                mem(MemoryKind.DIRECT, MemoryEffect.WRITE)
            )
        }
        define(Opcode.STOREHR, "BWLF") { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE),
                HardwareSlotSchema()
            )
        }
        define(Opcode.STOREI, "BWLF", memoryEffect = MemoryEffect.WRITE) { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE),
                mem(MemoryKind.INDIRECT, MemoryEffect.WRITE)
            )
        }
        define(Opcode.STOREHFACZERO, "F") { listOf(freg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)) }
        define(Opcode.STOREHFACONE, "F") { listOf(freg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)) }

        define(Opcode.LOADP_INC, "BWL", memoryEffect = MemoryEffect.READ_WRITE) {
            listOf(
                ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                mem(MemoryKind.DIRECT, MemoryEffect.READ_WRITE)
            )
        }
        define(Opcode.STOREP_INC, "BWL", memoryEffect = MemoryEffect.READ_WRITE) {
            listOf(
                ireg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE),
                mem(MemoryKind.DIRECT, MemoryEffect.READ_WRITE)
            )
        }

        // ---- control flow ----

        define(Opcode.JUMP, "N", controlFlow = ControlFlowEffect.JUMP) { listOf(TargetSlotSchema(TargetKind.STATIC)) }
        define(Opcode.JUMPI, "N", controlFlow = ControlFlowEffect.JUMP) { listOf(TargetSlotSchema(TargetKind.INDIRECT)) }
        define(Opcode.CALLI, "N", memoryEffect = MemoryEffect.UNKNOWN, statusEffect = StatusEffect.UNKNOWN, controlFlow = ControlFlowEffect.CALL) {
            listOf(CallSiteSlotSchema(CallKind.INDIRECT))
        }
        define(Opcode.CALL, "N", memoryEffect = MemoryEffect.UNKNOWN, statusEffect = StatusEffect.UNKNOWN, controlFlow = ControlFlowEffect.CALL) {
            listOf(CallSiteSlotSchema(CallKind.NORMAL))
        }
        define(Opcode.CALLFAR, "N", memoryEffect = MemoryEffect.UNKNOWN, statusEffect = StatusEffect.UNKNOWN, controlFlow = ControlFlowEffect.CALL) {
            listOf(CallSiteSlotSchema(CallKind.FAR))
        }
        define(Opcode.CALLFARVB, "N", memoryEffect = MemoryEffect.UNKNOWN, statusEffect = StatusEffect.UNKNOWN, controlFlow = ControlFlowEffect.CALL) {
            listOf(CallSiteSlotSchema(CallKind.FAR_VARBANK))
        }
        define(Opcode.SYSCALL, "N", memoryEffect = MemoryEffect.UNKNOWN, statusEffect = StatusEffect.UNKNOWN, controlFlow = ControlFlowEffect.CALL) {
            listOf(CallSiteSlotSchema(CallKind.SYSCALL))
        }
        define(Opcode.RETURN, "N", controlFlow = ControlFlowEffect.RETURN) { emptyList() }
        define(Opcode.RETURNR, "BWLF", controlFlow = ControlFlowEffect.RETURN) { t ->
            listOf(reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE))
        }
        define(Opcode.RETURNI, "BWLF", controlFlow = ControlFlowEffect.RETURN) { listOf(imm()) }

        for (branch in listOf(Opcode.BSTCC, Opcode.BSTCS, Opcode.BSTEQ, Opcode.BSTNE, Opcode.BSTNEG, Opcode.BSTPOS, Opcode.BSTVC, Opcode.BSTVS)) {
            define(branch, "N", statusEffect = StatusEffect.READS, controlFlow = ControlFlowEffect.BRANCH) {
                listOf(TargetSlotSchema(TargetKind.STATIC))
            }
        }
        for (branch in listOf(Opcode.BGTR, Opcode.BGTSR, Opcode.BGER, Opcode.BGESR)) {
            define(branch, "BWL", controlFlow = ControlFlowEffect.BRANCH) {
                listOf(
                    ireg(InstructionSlot.SRC_A, OperandRole.LEFT, OperandDirection.USE),
                    ireg(InstructionSlot.SRC_B, OperandRole.RIGHT, OperandDirection.USE),
                    TargetSlotSchema(TargetKind.STATIC)
                )
            }
        }
        for (branch in listOf(Opcode.BGT, Opcode.BLT, Opcode.BGTS, Opcode.BLTS, Opcode.BGE, Opcode.BLE, Opcode.BGES, Opcode.BLES)) {
            define(branch, "BWL", controlFlow = ControlFlowEffect.BRANCH) {
                listOf(
                    ireg(InstructionSlot.SRC_A, OperandRole.LEFT, OperandDirection.USE),
                    imm(OperandRole.RIGHT),
                    TargetSlotSchema(TargetKind.STATIC)
                )
            }
        }

        // ---- arithmetic ----

        for (opcode in listOf(Opcode.INC, Opcode.DEC, Opcode.NEG)) {
            define(opcode, "BWLF") { t -> listOf(reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF)) }
        }
        for (opcode in listOf(Opcode.INCM, Opcode.DECM, Opcode.NEGM, Opcode.INVM)) {
            val types = if (opcode == Opcode.INVM) "BWL" else "BWLF"
            define(opcode, types, memoryEffect = MemoryEffect.READ_WRITE) { listOf(mem(MemoryKind.DIRECT, MemoryEffect.READ_WRITE)) }
        }

        // register-register binary operations that modify the destination in place
        for (opcode in listOf(Opcode.ADDR, Opcode.SUBR, Opcode.MULR, Opcode.MULSR, Opcode.DIVR, Opcode.DIVSR, Opcode.FPOW)) {
            val types = when (opcode) {
                Opcode.FPOW -> "F"
                else -> "BWLF"
            }
            define(opcode, types) { t ->
                listOf(
                    reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF),
                    reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.RIGHT, OperandDirection.USE)
                )
            }
        }
        for (opcode in listOf(Opcode.MODR, Opcode.MODSR, Opcode.ANDR, Opcode.ORR, Opcode.XORR)) {
            define(opcode, "BWL") {
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF),
                    ireg(InstructionSlot.SRC_A, OperandRole.RIGHT, OperandDirection.USE)
                )
            }
        }
        // register-immediate binary operations that modify the destination in place
        for (opcode in listOf(Opcode.ADD, Opcode.SUB, Opcode.MUL, Opcode.MULS, Opcode.DIV, Opcode.DIVS)) {
            define(opcode, "BWLF") { t ->
                listOf(
                    reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF),
                    imm(OperandRole.RIGHT)
                )
            }
        }
        for (opcode in listOf(Opcode.MOD, Opcode.MODS, Opcode.AND, Opcode.OR, Opcode.XOR)) {
            define(opcode, "BWL") {
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF),
                    imm(OperandRole.RIGHT)
                )
            }
        }
        // memory operations: register operand + memory destination
        for (opcode in listOf(Opcode.ADDM, Opcode.SUBM, Opcode.MULM, Opcode.MULSM, Opcode.DIVM, Opcode.DIVSM)) {
            define(opcode, "BWLF", memoryEffect = MemoryEffect.READ_WRITE) { t ->
                listOf(
                    reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.RIGHT, OperandDirection.USE),
                    mem(MemoryKind.DIRECT, MemoryEffect.READ_WRITE)
                )
            }
        }
        for (opcode in listOf(Opcode.ANDM, Opcode.ORM, Opcode.XORM)) {
            define(opcode, "BWL", memoryEffect = MemoryEffect.READ_WRITE) {
                listOf(
                    ireg(InstructionSlot.SRC_A, OperandRole.RIGHT, OperandDirection.USE),
                    mem(MemoryKind.DIRECT, MemoryEffect.READ_WRITE)
                )
            }
        }
        for (opcode in listOf(Opcode.ADDIM, Opcode.SUBIM)) {
            define(opcode, "BWLF", memoryEffect = MemoryEffect.READ_WRITE) {
                listOf(
                    imm(OperandRole.RIGHT),
                    mem(MemoryKind.DIRECT, MemoryEffect.READ_WRITE)
                )
            }
        }

        define(Opcode.DIVMODR, "BWL") {
            listOf(
                ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF),
                ireg(InstructionSlot.DEST_B, OperandRole.SECONDARY_RESULT, OperandDirection.USE_DEF)
            )
        }
        define(Opcode.SDIVMODR, "BWL") {
            listOf(
                ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF),
                ireg(InstructionSlot.DEST_B, OperandRole.SECONDARY_RESULT, OperandDirection.USE_DEF)
            )
        }
        for (opcode in listOf(Opcode.DIVMOD, Opcode.SDIVMOD)) {
            define(opcode, "BWL") {
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF),
                    ireg(InstructionSlot.DEST_B, OperandRole.SECONDARY_RESULT, OperandDirection.DEF),
                    imm(OperandRole.RIGHT)
                )
            }
        }

        define(Opcode.SQRT, "BWLF") { t ->
            val destType = when (t) {
                IRDataType.LONG -> IRDataType.WORD
                IRDataType.FLOAT -> null
                else -> IRDataType.BYTE
            }
            if (t == IRDataType.FLOAT)
                listOf(
                    freg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                    freg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)
                )
            else
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF, TypeRule.Fixed(destType!!)),
                    ireg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)
                )
        }
        define(Opcode.SQUARE, "BWLF") { t ->
            listOf(
                reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)
            )
        }
        define(Opcode.SGN, "BWLF", statusEffect = StatusEffect.SETS) { t ->
            listOf(
                ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF, TypeRule.Fixed(IRDataType.BYTE)),
                reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)
            )
        }
        define(Opcode.CMP, "BWL", statusEffect = StatusEffect.SETS) {
            listOf(
                ireg(InstructionSlot.SRC_A, OperandRole.LEFT, OperandDirection.USE),
                ireg(InstructionSlot.SRC_B, OperandRole.RIGHT, OperandDirection.USE)
            )
        }
        define(Opcode.CMPI, "BWL", statusEffect = StatusEffect.SETS) {
            listOf(
                ireg(InstructionSlot.SRC_A, OperandRole.LEFT, OperandDirection.USE),
                imm(OperandRole.RIGHT)
            )
        }
        for (opcode in listOf(Opcode.EXT, Opcode.EXTS)) {
            define(opcode, "BWL") { t ->
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF, TypeRule.Fixed(widened(t))),
                    ireg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)
                )
            }
        }
        for (opcode in listOf(Opcode.EXTL, Opcode.EXTLS)) {
            define(opcode, "B") {
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF, TypeRule.Fixed(IRDataType.LONG)),
                    ireg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)
                )
            }
        }

        // ---- bit operations, shifts and rotates ----

        define(Opcode.INV, "BWL") { listOf(ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF)) }
        for (opcode in listOf(Opcode.ASRN, Opcode.LSRN, Opcode.LSLN)) {
            define(opcode, "BWL", statusEffect = StatusEffect.SETS) {
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF),
                    ireg(InstructionSlot.SRC_A, OperandRole.RIGHT, OperandDirection.USE, TypeRule.Fixed(IRDataType.BYTE))
                )
            }
        }
        for (opcode in listOf(Opcode.ASRNM, Opcode.LSRNM, Opcode.LSLNM)) {
            define(opcode, "BWL", memoryEffect = MemoryEffect.READ_WRITE, statusEffect = StatusEffect.SETS) {
                listOf(
                    ireg(InstructionSlot.SRC_A, OperandRole.RIGHT, OperandDirection.USE, TypeRule.Fixed(IRDataType.BYTE)),
                    mem(MemoryKind.DIRECT, MemoryEffect.READ_WRITE)
                )
            }
        }
        for (opcode in listOf(Opcode.ASRI, Opcode.LSRI, Opcode.LSLI)) {
            define(opcode, "BWL", statusEffect = StatusEffect.SETS) {
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF),
                    imm(OperandRole.RIGHT)
                )
            }
        }
        for (opcode in listOf(Opcode.ASR, Opcode.LSR, Opcode.LSL, Opcode.ROR, Opcode.ROL)) {
            define(opcode, "BWL", statusEffect = StatusEffect.SETS) {
                listOf(ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF))
            }
        }
        for (opcode in listOf(Opcode.ROXR, Opcode.ROXL)) {
            define(opcode, "BWL", statusEffect = StatusEffect.READS_AND_SETS) {
                listOf(ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF))
            }
        }
        for (opcode in listOf(Opcode.ASRM, Opcode.LSRM, Opcode.LSLM, Opcode.RORM, Opcode.ROLM)) {
            define(opcode, "BWL", memoryEffect = MemoryEffect.READ_WRITE, statusEffect = StatusEffect.SETS) {
                listOf(mem(MemoryKind.DIRECT, MemoryEffect.READ_WRITE))
            }
        }
        for (opcode in listOf(Opcode.ROXRM, Opcode.ROXLM)) {
            define(opcode, "BWL", memoryEffect = MemoryEffect.READ_WRITE, statusEffect = StatusEffect.READS_AND_SETS) {
                listOf(mem(MemoryKind.DIRECT, MemoryEffect.READ_WRITE))
            }
        }
        define(Opcode.BITTST, "BWL", statusEffect = StatusEffect.SETS) {
            listOf(
                ireg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE),
                imm(OperandRole.RIGHT, TypeRule.Fixed(IRDataType.BYTE))
            )
        }
        for (opcode in listOf(Opcode.BITSET, Opcode.BITCLR, Opcode.BITTOG)) {
            define(opcode, "BWL") {
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.USE_DEF),
                    imm(OperandRole.RIGHT, TypeRule.Fixed(IRDataType.BYTE))
                )
            }
        }

        // ---- floating point conversions ----

        val fromIntegerTypes = mapOf(
            Opcode.FFROMUB to IRDataType.BYTE,
            Opcode.FFROMSB to IRDataType.BYTE,
            Opcode.FFROMUW to IRDataType.WORD,
            Opcode.FFROMSW to IRDataType.WORD,
            Opcode.FFROMSL to IRDataType.LONG
        )
        for ((opcode, intType) in fromIntegerTypes) {
            define(opcode, "F") {
                listOf(
                    freg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                    ireg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE, TypeRule.Fixed(intType))
                )
            }
        }
        val toIntegerTypes = mapOf(
            Opcode.FTOUB to IRDataType.BYTE,
            Opcode.FTOSB to IRDataType.BYTE,
            Opcode.FTOUW to IRDataType.WORD,
            Opcode.FTOSW to IRDataType.WORD,
            Opcode.FTOSL to IRDataType.LONG
        )
        for ((opcode, intType) in toIntegerTypes) {
            define(opcode, "F") {
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF, TypeRule.Fixed(intType)),
                    freg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)
                )
            }
        }
        for (opcode in listOf(Opcode.FABS, Opcode.FSIN, Opcode.FCOS, Opcode.FTAN, Opcode.FATAN, Opcode.FLN, Opcode.FLOG,
            Opcode.FROUND, Opcode.FFLOOR, Opcode.FCEIL)) {
            define(opcode, "F") {
                listOf(
                    freg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF),
                    freg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)
                )
            }
        }
        define(Opcode.FCOMP, "F") {
            listOf(
                ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF, TypeRule.Fixed(IRDataType.BYTE)),
                freg(InstructionSlot.SRC_A, OperandRole.LEFT, OperandDirection.USE),
                freg(InstructionSlot.SRC_B, OperandRole.RIGHT, OperandDirection.USE)
            )
        }

        // ---- misc ----

        for (opcode in listOf(Opcode.CLC, Opcode.SEC, Opcode.CLI, Opcode.SEI))
            define(opcode, "N", statusEffect = StatusEffect.SETS) { emptyList() }
        define(Opcode.PUSH, "BWLF") { t -> listOf(reg(fileOf(t), InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)) }
        define(Opcode.POP, "BWLF") { t -> listOf(reg(fileOf(t), InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF)) }
        define(Opcode.PUSHST, "N", statusEffect = StatusEffect.READS) { emptyList() }
        define(Opcode.POPST, "N", statusEffect = StatusEffect.SETS) { emptyList() }

        val extractions = listOf(
            Triple(Opcode.LSIGB, "WL", IRDataType.BYTE),
            Triple(Opcode.MSIGB, "WL", IRDataType.BYTE),
            Triple(Opcode.LSIGW, "L", IRDataType.WORD),
            Triple(Opcode.MSIGW, "L", IRDataType.WORD),
            Triple(Opcode.BSIGB, "L", IRDataType.BYTE),
            Triple(Opcode.MIDB, "L", IRDataType.BYTE)
        )
        for ((opcode, types, destType) in extractions) {
            define(opcode, types) {
                listOf(
                    ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF, TypeRule.Fixed(destType)),
                    ireg(InstructionSlot.SRC_A, OperandRole.VALUE, OperandDirection.USE)
                )
            }
        }
        define(Opcode.CONCAT, "BW") { t ->
            listOf(
                ireg(InstructionSlot.DEST, OperandRole.RESULT, OperandDirection.DEF, TypeRule.Fixed(widened(t))),
                ireg(InstructionSlot.SRC_A, OperandRole.LEFT, OperandDirection.USE),
                ireg(InstructionSlot.SRC_B, OperandRole.RIGHT, OperandDirection.USE)
            )
        }
        define(Opcode.BREAKPOINT, "N") { emptyList() }
        define(Opcode.ALIGN, "N") { listOf(imm(OperandRole.VALUE, TypeRule.Fixed(IRDataType.LONG))) }

        require(result.keys == Opcode.entries.toSet()) {
            "opcode schema is incomplete, missing: ${Opcode.entries.toSet() - result.keys}"
        }
        return result
    }
}
