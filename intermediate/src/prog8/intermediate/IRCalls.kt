package prog8.intermediate

import prog8.code.core.Statusflag

/*
Structured call model.
----------------------

Calls (CALL, CALLI, CALLFAR, CALLFARVB, SYSCALL) carry a single CallSite operand that describes
what is called, how the arguments are passed, where the results end up, and what the call does
to memory and the status flags.
*/


/** what is being called */
sealed interface CallTarget {
    /** a regular call to a label, a fixed address, or indirectly through a register */
    data class Direct(val reference: CodeReference, val externalName: String? = null) : CallTarget

    /** a call into another ram/rom bank, with the bank number as a constant */
    data class Banked(val bank: Int, val reference: CodeReference, val externalName: String? = null) : CallTarget

    /** a call into another ram/rom bank, with the bank number in a register */
    data class BankedVariable(val bankRegister: RegisterOperand, val reference: CodeReference, val externalName: String? = null) : CallTarget {
        init {
            require(!bankRegister.isFloat) { "bank register must be an integer register" }
            require(bankRegister.role == OperandRole.VALUE) { "bank register must have VALUE role" }
            require(bankRegister.direction == OperandDirection.USE) { "bank register must be a USE operand" }
        }
    }

    /** amiga library call: library number + (negative) LVO offset into the library's jump table */
    data class AmigaLibrary(val library: Int, val lvo: Int, val name: String? = null) : CallTarget {
        init {
            require(lvo < 0) { "amiga library call offsets should all be <0: $lvo" }
        }
    }

    /** a system call of the virtual machine / emulation layer */
    data class SystemCall(val number: Int) : CallTarget
}


/** where an argument comes from, or where a result is delivered */
sealed interface CallLocation {
    /** no explicit location: value simply lives in the given virtual register */
    data object Default : CallLocation

    /** the value is passed via the memory location of a subroutine parameter variable */
    data class ParameterMemory(val name: String, val address: MemoryAddress? = null) : CallLocation {
        init {
            require(name.isNotEmpty() || address != null) { "parameter-memory location needs a name or address" }
            if (name.isNotEmpty())
                requireValidIRSymbolName(name)
        }
    }

    /** the value is passed in a cpu hardware register (abstract calling convention slot) */
    data class HardwareRegister(val slot: CallingConventionSlot) : CallLocation

    /** the value is passed in a cpu status flag */
    data class StatusFlag(val flag: Statusflag) : CallLocation
}


/** one argument of a call */
data class CallArgument(val source: RegisterOperand, val location: CallLocation = CallLocation.Default) {
    init {
        require(source.role == OperandRole.CALL_ARGUMENT) { "call argument must have CALL_ARGUMENT role" }
        require(source.direction == OperandDirection.USE) { "call argument must be a USE operand" }
    }
}


/**
 * One result of a call. The destination register is null if the result isn't captured in a
 * virtual register (for instance a status flag result that is only used by a following branch).
 */
data class CallResult(val destination: RegisterOperand?, val location: CallLocation = CallLocation.Default) {
    init {
        if (destination != null) {
            require(destination.role == OperandRole.CALL_RESULT) { "call result must have CALL_RESULT role" }
            require(destination.direction == OperandDirection.DEF) { "call result must be a DEF operand" }
        } else {
            require(location != CallLocation.Default) { "a call result without destination register needs an explicit location" }
        }
    }

    val type: IRDataType?
        get() = destination?.type
}


/** what the call does besides producing its results */
data class CallEffects(
    val memoryEffect: MemoryEffect = MemoryEffect.UNKNOWN,
    val statusEffect: StatusEffect = StatusEffect.UNKNOWN
) {
    companion object {
        val DEFAULT = CallEffects()
    }
}


/** a complete description of a call: target, arguments, results, effects */
data class CallSite(
    val target: CallTarget,
    val arguments: List<CallArgument> = emptyList(),
    val results: List<CallResult> = emptyList(),
    val effects: CallEffects = CallEffects.DEFAULT
) {
    val registerAccesses: List<RegisterOperand>
        get() = buildList {
            when (val t = target) {
                is CallTarget.Direct -> (t.reference as? CodeReference.Indirect)?.let { add(it.pointer) }
                is CallTarget.Banked -> (t.reference as? CodeReference.Indirect)?.let { add(it.pointer) }
                is CallTarget.BankedVariable -> {
                    add(t.bankRegister)
                    (t.reference as? CodeReference.Indirect)?.let { add(it.pointer) }
                }
                is CallTarget.AmigaLibrary -> {}
                is CallTarget.SystemCall -> {}
            }
            arguments.forEach { add(it.source) }
            results.forEach { r -> r.destination?.let { add(it) } }
        }

    /** the code reference this call jumps to, if it is a static one */
    val codeReference: CodeReference?
        get() = when (val t = target) {
            is CallTarget.Direct -> t.reference
            is CallTarget.Banked -> t.reference
            is CallTarget.BankedVariable -> t.reference
            is CallTarget.AmigaLibrary -> null
            is CallTarget.SystemCall -> null
        }

    /** the symbolic name of the called routine, if known */
    val externalName: String?
        get() = when (val t = target) {
            is CallTarget.Direct -> t.externalName
            is CallTarget.Banked -> t.externalName
            is CallTarget.BankedVariable -> t.externalName
            is CallTarget.AmigaLibrary -> t.name
            is CallTarget.SystemCall -> null
        }

    fun mapRegisters(transform: (VirtualRegister) -> VirtualRegister): CallSite {
        fun mapRef(ref: CodeReference): CodeReference =
            if (ref is CodeReference.Indirect) ref.copy(pointer = ref.pointer.withRegister(transform(ref.pointer.register))) else ref

        val newTarget = when (val t = target) {
            is CallTarget.Direct -> t.copy(reference = mapRef(t.reference))
            is CallTarget.Banked -> t.copy(reference = mapRef(t.reference))
            is CallTarget.BankedVariable -> t.copy(
                bankRegister = t.bankRegister.withRegister(transform(t.bankRegister.register)),
                reference = mapRef(t.reference)
            )
            is CallTarget.AmigaLibrary -> t
            is CallTarget.SystemCall -> t
        }
        return copy(
            target = newTarget,
            arguments = arguments.map { it.copy(source = it.source.withRegister(transform(it.source.register))) },
            results = results.map { r -> r.copy(destination = r.destination?.let { it.withRegister(transform(it.register)) }) }
        )
    }

    fun withTarget(reference: CodeReference): CallSite {
        val newTarget = when (val t = target) {
            is CallTarget.Direct -> t.copy(reference = reference)
            is CallTarget.Banked -> t.copy(reference = reference)
            is CallTarget.BankedVariable -> t.copy(reference = reference)
            is CallTarget.AmigaLibrary -> throw IllegalArgumentException("cannot set code target on an amiga library call")
            is CallTarget.SystemCall -> throw IllegalArgumentException("cannot set code target on a syscall")
        }
        return copy(target = newTarget)
    }
}


/** convenience builders for call operands */
object Calls {
    fun argument(
        register: VirtualRegister,
        type: IRDataType,
        location: CallLocation = CallLocation.Default
    ) = CallArgument(RegisterOperand(register, type, OperandRole.CALL_ARGUMENT, OperandDirection.USE), location)

    fun argument(
        registerNumber: Int,
        type: IRDataType,
        location: CallLocation = CallLocation.Default
    ) = argument(registerFor(registerNumber, type), type, location)

    fun result(
        register: VirtualRegister,
        type: IRDataType,
        location: CallLocation = CallLocation.Default
    ) = CallResult(RegisterOperand(register, type, OperandRole.CALL_RESULT, OperandDirection.DEF), location)

    fun result(
        registerNumber: Int,
        type: IRDataType,
        location: CallLocation = CallLocation.Default
    ) = result(registerFor(registerNumber, type), type, location)

    fun registerFor(registerNumber: Int, type: IRDataType): VirtualRegister =
        if (type == IRDataType.FLOAT) VirtualRegister.float(registerNumber) else VirtualRegister.int(registerNumber)
}
