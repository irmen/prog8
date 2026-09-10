package prog8.intermediate

/*
Structured IR operands.
-----------------------

The IR instruction set uses typed, structured operands instead of positional nullable fields.
Every operand knows what it is (register, immediate, memory reference, code reference,
hardware slot), what data type it has, what semantic role it plays in the instruction,
and whether it is read, written, or both.

The single source of truth for which operands an opcode has, is OpcodeSchema.
*/


/**
 * A virtual register. Integer registers and floating point registers are separate register files:
 * r5 and fr5 are two DIFFERENT registers, and comparing them yields false.
 */
sealed interface VirtualRegister {
    val number: RegisterNum

    @JvmInline
    value class IntReg(override val number: RegisterNum) : VirtualRegister {
        override fun toString(): String = "r${number.value}"
    }

    @JvmInline
    value class FloatReg(override val number: RegisterNum) : VirtualRegister {
        override fun toString(): String = "fr${number.value}"
    }

    companion object {
        fun int(number: Int) = IntReg(RegisterNum(number))
        fun float(number: Int) = FloatReg(RegisterNum(number))
    }
}

val VirtualRegister.num: Int
    get() = number.value

val VirtualRegister.isFloat: Boolean
    get() = this is VirtualRegister.FloatReg


/** how an operand is accessed by the instruction */
enum class OperandDirection { USE, DEF, USE_DEF }

/** the semantic role an operand plays in the instruction */
enum class OperandRole {
    RESULT, SECONDARY_RESULT, VALUE, LEFT, RIGHT, INDEX, POINTER_BASE,
    INDIRECT_TARGET, CALL_ARGUMENT, CALL_RESULT
}

/** hint for machine register allocators (data register vs address register on m68k, for instance) */
enum class AllocationHint { NONE, PREFER_DATA, PREFER_ADDRESS }


/** access to a virtual register, with its data type, role and direction */
data class RegisterOperand(
    val register: VirtualRegister,
    val type: IRDataType,
    val role: OperandRole,
    val direction: OperandDirection,
    val allocationHint: AllocationHint = AllocationHint.NONE
) {
    init {
        require((register is VirtualRegister.FloatReg) == (type == IRDataType.FLOAT)) {
            "float register must have float type and vice versa: $register $type"
        }
    }

    val number: RegisterNum get() = register.number
    val registerNumber: Int get() = register.number.value
    val isFloat: Boolean get() = register is VirtualRegister.FloatReg

    fun withRegister(newRegister: VirtualRegister): RegisterOperand =
        if (newRegister == register) this else copy(register = newRegister)
}


/** an immediate (constant) operand: an integer, a float, or the address of a symbol */
sealed interface ImmediateOperand {
    val type: IRDataType

    data class Integer(val value: Int, override val type: IRDataType) : ImmediateOperand {
        init {
            require(type != IRDataType.FLOAT) { "integer immediate can't have float type" }
        }
    }

    data class FloatValue(val value: Double) : ImmediateOperand {
        override val type: IRDataType get() = IRDataType.FLOAT
    }

    /** the address of a symbol (optionally with a positive offset added to it) */
    data class SymbolAddress(val symbol: String, val offset: Int = 0, override val type: IRDataType = IRDataType.POINTER) : ImmediateOperand {
        init {
            requireValidIRSymbolName(symbol)
            require(offset >= 0) { "symbol address offset must be >=0: $offset" }
            require(type != IRDataType.FLOAT) { "symbol address can't have float type" }
        }
    }
}

/** the integer value of an immediate, or null if it isn't a plain integer */
val ImmediateOperand.integerValue: Int?
    get() = (this as? ImmediateOperand.Integer)?.value


/** a cpu hardware register, addressed via an abstract calling convention slot */
data class HardwareSlotOperand(val slot: CallingConventionSlot, val type: IRDataType)


/** base address of a memory reference: a symbol, or a fixed numeric address */
sealed interface AddressBase {
    data class Symbol(val name: String) : AddressBase {
        init {
            requireValidIRSymbolName(name)
        }
    }

    data class Absolute(val address: MemoryAddress) : AddressBase
}


/** a reference to a memory location */
sealed interface MemoryReference {
    /** memory at (base + displacement) */
    data class Direct(val base: AddressBase, val displacement: Int = 0) : MemoryReference {
        init {
            require(displacement >= 0) { "memory displacement must be >=0: $displacement" }
        }
    }

    /** memory at (base + displacement + index*scale). Index is an element index, scale is the element size. */
    data class Indexed(
        val base: AddressBase,
        val index: RegisterOperand,
        val scale: Int = 1,
        val displacement: Int = 0
    ) : MemoryReference {
        init {
            require(scale in 1..65535) { "index scale out of range: $scale" }
            require(displacement >= 0) { "memory displacement must be >=0: $displacement" }
            require(!index.isFloat) { "index register must be an integer register" }
            require(index.role == OperandRole.INDEX) { "index register must have INDEX role" }
            require(index.direction == OperandDirection.USE) { "index register must be a USE operand" }
        }
    }

    /** memory pointed to by a register, plus a displacement (used for pointer/struct field access) */
    data class Indirect(val pointer: RegisterOperand, val displacement: Int = 0) : MemoryReference {
        init {
            require(displacement in 0..65535) { "indirect displacement out of range: $displacement" }
            require(!pointer.isFloat) { "pointer register must be an integer register" }
            require(pointer.role == OperandRole.POINTER_BASE) { "pointer register must have POINTER_BASE role" }
            require(pointer.direction == OperandDirection.USE) { "pointer register must be a USE operand" }
        }
    }
}

/** the register operands that occur inside a memory reference (index or pointer register) */
val MemoryReference.registers: List<RegisterOperand>
    get() = when (this) {
        is MemoryReference.Direct -> emptyList()
        is MemoryReference.Indexed -> listOf(index)
        is MemoryReference.Indirect -> listOf(pointer)
    }

fun MemoryReference.mapRegisters(transform: (VirtualRegister) -> VirtualRegister): MemoryReference =
    when (this) {
        is MemoryReference.Direct -> this
        is MemoryReference.Indexed -> copy(index = index.withRegister(transform(index.register)))
        is MemoryReference.Indirect -> copy(pointer = pointer.withRegister(transform(pointer.register)))
    }

/** the symbol name this memory reference is based on, if any */
val MemoryReference.symbolName: String?
    get() = when (this) {
        is MemoryReference.Direct -> (base as? AddressBase.Symbol)?.name
        is MemoryReference.Indexed -> (base as? AddressBase.Symbol)?.name
        is MemoryReference.Indirect -> null
    }

/** the fixed numeric address this memory reference is based on, if any */
val MemoryReference.absoluteAddress: MemoryAddress?
    get() = when (this) {
        is MemoryReference.Direct -> (base as? AddressBase.Absolute)?.address
        is MemoryReference.Indexed -> (base as? AddressBase.Absolute)?.address
        is MemoryReference.Indirect -> null
    }


/** a reference to a location in the code (branch/jump/call target) */
sealed interface CodeReference {
    data class Label(val name: String, val offset: Int = 0) : CodeReference {
        init {
            requireValidIRSymbolName(name)
            require(offset >= 0) { "label offset must be >=0: $offset" }
        }
    }

    data class Absolute(val address: MemoryAddress) : CodeReference

    /** jump/call to the address contained in a register */
    data class Indirect(val pointer: RegisterOperand) : CodeReference {
        init {
            require(!pointer.isFloat) { "indirect code target register must be an integer register" }
            require(pointer.role == OperandRole.INDIRECT_TARGET) { "indirect code target register must have INDIRECT_TARGET role" }
            require(pointer.direction == OperandDirection.USE) { "indirect code target register must be a USE operand" }
        }
    }
}

val CodeReference.labelName: String?
    get() = (this as? CodeReference.Label)?.name


/** what an instruction does to memory */
enum class MemoryEffect { NONE, READ, WRITE, READ_WRITE, UNKNOWN }

/** what an instruction does to the cpu status flags */
enum class StatusEffect { NONE, SETS, READS, READS_AND_SETS, UNKNOWN }

/** what an instruction does to the control flow */
enum class ControlFlowEffect { FALLTHROUGH, BRANCH, JUMP, CALL, RETURN }


internal fun requireValidIRSymbolName(name: String) {
    require(name.isNotEmpty()) { "empty symbol name" }
    require(name.first().isLetter()) {
        "label/symbol should start with a letter: $name"
    }
    require(name.last() != '.' && ".." !in name) { "label/symbol has an empty name segment: $name" }
    require(name.all { (it.isJavaIdentifierStart() || it.isJavaIdentifierPart() || it == '.') && it != '$' }) {
        "label/symbol contains invalid character: $name"
    }
}
