package prog8.codegen.cpu6502.assignment

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal


internal enum class TargetStorageKind {
    VARIABLE,       // non-pointer variable
    ARRAY,
    MEMORY,
    REGISTER,
    POINTER,        // wherever the pointer variable points to
    VOID            // assign nothing - used in multi-value assigns for void placeholders
}

internal enum class SourceStorageKind {
    LITERALBOOLEAN,
    LITERALNUMBER,
    VARIABLE,
    ARRAY,
    MEMORY,
    REGISTER,
    EXPRESSION,         // expression in ast-form, still to be evaluated
}

internal class AsmAssignTarget(val kind: TargetStorageKind,
                               private val asmgen: AsmGen6502Internal,
                               val datatype: DataType,
                               val scope: IPtSubroutine?,
                               val position: Position,
                               private val variableAsmName: String? = null,
                               val array: PtArrayIndexer? = null,
                               val memory: PtMemoryByte? = null,
                               val register: RegisterOrPair? = null,
                               val pointer: PtPointerDeref? = null,
                               val origAstTarget: PtAssignTarget? = null
                               )
{
    val constArrayIndexValue by lazy { array?.index?.asConstInteger()?.toUInt() }
    val asmVarname: String by lazy {
        if (array == null)
            variableAsmName!!
        else {
            if(array.variable==null)
                TODO("asmVarname for array with pointer")
            asmgen.asmVariableName(array.variable!!)
        }
    }

    init {
        if(register!=null && !datatype.isNumericOrBool)
            throw AssemblyError("must be numeric type")
        if(kind==TargetStorageKind.REGISTER)
            require(register!=null)
        else
            require(register==null)
        if(kind==TargetStorageKind.POINTER)
            require(pointer!=null)
        if(pointer!=null)
            require(kind==TargetStorageKind.POINTER)
    }

    companion object {
        fun fromAstAssignmentMulti(targets: List<PtAssignTarget>, definingSub: IPtSubroutine?, asmgen: AsmGen6502Internal): List<AsmAssignTarget> {
            return targets.map {
                if(it.void)
                    AsmAssignTarget(TargetStorageKind.VOID, asmgen, DataType.UNDEFINED, null, it.position)
                else
                    fromAstAssignment(it, definingSub, asmgen)
            }
        }

        fun fromAstAssignment(target: PtAssignTarget, definingSub: IPtSubroutine?, asmgen: AsmGen6502Internal): AsmAssignTarget {
            with(target) {
                when {
                    identifier != null -> {
                        val parameter = asmgen.findSubroutineParameter(identifier!!.name, asmgen)
                        if (parameter!=null) {
                            val sub = parameter.definingAsmSub()
                            if (sub!=null) {
                                val reg = sub.parameters.single { it.second===parameter }.first
                                if(reg.statusflag!=null)
                                    throw AssemblyError("can't assign value to processor statusflag directly")
                                else
                                    return AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, type, definingSub, target.position, register=reg.registerOrPair, origAstTarget = this)
                            }
                        }
                        return AsmAssignTarget(TargetStorageKind.VARIABLE, asmgen, type, definingSub, target.position, variableAsmName = asmgen.asmVariableName(identifier!!), origAstTarget =  this)
                    }
                    array != null -> return AsmAssignTarget(TargetStorageKind.ARRAY, asmgen, type, definingSub, target.position, array = array, origAstTarget =  this)
                    memory != null -> return AsmAssignTarget(TargetStorageKind.MEMORY, asmgen, type, definingSub, target.position, memory =  memory, origAstTarget =  this)
                    pointerDeref != null -> return AsmAssignTarget(TargetStorageKind.POINTER, asmgen, type, definingSub, target.position, pointer = pointerDeref, origAstTarget =  this)
                    else -> throw AssemblyError("weird target")
                }
            }
        }

        fun fromRegisters(registers: RegisterOrPair, signed: Boolean, pos: Position, scope: IPtSubroutine?, asmgen: AsmGen6502Internal): AsmAssignTarget =
                when(registers) {
                    RegisterOrPair.A,
                    RegisterOrPair.X,
                    RegisterOrPair.Y -> {
                        val dt = if(signed) DataType.BYTE else DataType.UBYTE
                        AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, dt, scope, pos, register = registers)
                    }
                    RegisterOrPair.AX,
                    RegisterOrPair.AY,
                    RegisterOrPair.XY -> {
                        val dt = if(signed) DataType.WORD else DataType.UWORD
                        AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, dt, scope, pos, register = registers)
                    }
                    RegisterOrPair.FAC1,
                    RegisterOrPair.FAC2 -> {
                        AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, DataType.FLOAT, scope, pos, register = registers)
                    }
                    RegisterOrPair.R0,
                    RegisterOrPair.R1,
                    RegisterOrPair.R2,
                    RegisterOrPair.R3,
                    RegisterOrPair.R4,
                    RegisterOrPair.R5,
                    RegisterOrPair.R6,
                    RegisterOrPair.R7,
                    RegisterOrPair.R8,
                    RegisterOrPair.R9,
                    RegisterOrPair.R10,
                    RegisterOrPair.R11,
                    RegisterOrPair.R12,
                    RegisterOrPair.R13,
                    RegisterOrPair.R14,
                    RegisterOrPair.R15 -> {
                        val dt = if(signed) DataType.WORD else DataType.UWORD
                        AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, dt, scope, pos, register = registers)
                    }
                }
    }

    fun isSameAs(left: PtExpression): Boolean =
        when(kind) {
            TargetStorageKind.VARIABLE -> {
                val scopedName: String = if('.' in asmVarname)
                    asmVarname
                else {
                    val scopeName = (scope as? PtNamedNode)?.scopedName
                    if (scopeName == null) asmVarname else "$scopeName.$asmVarname"
                }
                left is PtIdentifier && left.name==scopedName
            }
            TargetStorageKind.ARRAY -> {
                left is PtArrayIndexer && left isSameAs array!! && left.splitWords==array.splitWords && (left.pointerderef==null && array.pointerderef==null || left.pointerderef!! isSameAs array.pointerderef!!)
            }
            TargetStorageKind.MEMORY -> {
                left isSameAs memory!!
            }
            TargetStorageKind.POINTER -> {
                TODO("is pointer deref target same as expression? ${this.position}")
            }
            TargetStorageKind.REGISTER -> false
            TargetStorageKind.VOID -> false
        }
}

internal class AsmAssignSource(val kind: SourceStorageKind,
                               private val program: PtProgram,
                               private val asmgen: AsmGen6502Internal,
                               val datatype: DataType,
                               private val variableAsmName: String? = null,
                               val array: PtArrayIndexer? = null,
                               val memory: PtMemoryByte? = null,
                               val register: RegisterOrPair? = null,
                               val number: PtNumber? = null,
                               val boolean: PtBool? = null,
                               val expression: PtExpression? = null
)
{
    val asmVarname: String
        get() = if(array==null)
            variableAsmName!!
        else {
            if(array.variable==null)
                TODO("asmVarname for array with pointer")
            asmgen.asmVariableName(array.variable!!)
        }

    companion object {
        fun fromAstSource(value: PtExpression, program: PtProgram, asmgen: AsmGen6502Internal): AsmAssignSource {
            val cv = value as? PtNumber
            if(cv!=null)
                return AsmAssignSource(SourceStorageKind.LITERALNUMBER, program, asmgen, cv.type, number = cv)
            val bv = value as? PtBool
            if(bv!=null)
                return AsmAssignSource(SourceStorageKind.LITERALBOOLEAN, program, asmgen, DataType.BOOL, boolean = bv)

            return when(value) {
                // checked above:   is PtNumber -> throw AssemblyError("should have been constant value")
                is PtString -> throw AssemblyError("string literal value should not occur anymore for asm generation")
                is PtArray -> throw AssemblyError("array literal value should not occur anymore for asm generation")
                is PtIdentifier -> {
                    val parameter = asmgen.findSubroutineParameter(value.name, asmgen)
                    if(parameter?.definingAsmSub() != null)
                        throw AssemblyError("can't assign from a asmsub register parameter $value ${value.position}")
                    val varName=asmgen.asmVariableName(value)
                    // special case: "cx16.r[0-15]" are 16-bits virtual registers of the commander X16 system
                    if(value.type.isUnsignedWord && varName.lowercase().startsWith("cx16.r")) {
                        val regStr = varName.lowercase().substring(5)
                        val reg = RegisterOrPair.valueOf(regStr.uppercase())
                        AsmAssignSource(SourceStorageKind.REGISTER, program, asmgen, value.type, register = reg)
                    } else {
                        AsmAssignSource(SourceStorageKind.VARIABLE, program, asmgen, value.type, variableAsmName = varName)
                    }
                }
                is PtMemoryByte -> {
                    AsmAssignSource(SourceStorageKind.MEMORY, program, asmgen, DataType.UBYTE, memory = value)
                }
                is PtArrayIndexer -> {
                    AsmAssignSource(SourceStorageKind.ARRAY, program, asmgen, value.type, array = value)
                }
                is PtBuiltinFunctionCall -> {
                    AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, value.type, expression = value)
                }
                is PtFunctionCall -> {
                    val symbol = asmgen.symbolTable.lookup(value.name) ?: throw AssemblyError("lookup error ${value.name}")
                    val sub = symbol.astNode as IPtSubroutine
                    val returnType =
                        if(sub is PtSub && sub.signature.returns.size>1)
                            DataType.UNDEFINED      // TODO list of types instead?
                        else
                            sub.returnsWhatWhere().firstOrNull { rr -> rr.first.registerOrPair != null || rr.first.statusflag!=null }?.second
                            ?: throw AssemblyError("can't translate zero return values in assignment")
                    AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, returnType, expression = value)
                }
                else -> {
                    AsmAssignSource(SourceStorageKind.EXPRESSION, program, asmgen, value.type, expression = value)
                }
            }
        }
    }

    fun adjustSignedUnsigned(target: AsmAssignTarget): AsmAssignSource {
        // allow some signed/unsigned relaxations

        fun withAdjustedDt(newType: DataType) =
                AsmAssignSource(kind, program, asmgen, newType, variableAsmName, array, memory, register, number, boolean, expression)

        if(target.datatype!=datatype) {
            if(target.datatype.isByte && datatype.isByte) {
                return withAdjustedDt(target.datatype)
            } else if(target.datatype.isWord && datatype.isWord) {
                return withAdjustedDt(target.datatype)
            }
        }
        return this
    }

}


internal sealed class AsmAssignmentBase(val source: AsmAssignSource,
                                        val targets: List<AsmAssignTarget>,
                                        val memsizer: IMemSizer,
                                        val position: Position) {
    init {
        targets.forEach { target ->
            if (!source.datatype.isArray && !source.datatype.isUndefined && !target.datatype.isArray && !target.datatype.isUndefined)
                require(memsizer.memorySize(source.datatype, null) <= memsizer.memorySize(target.datatype, null)) {
                    "source dt size must be less or equal to target dt size at $position srcdt=${source.datatype} targetdt=${target.datatype}"
                }
        }
    }

    val target: AsmAssignTarget
        get() = targets.single()
}

internal class AsmAssignment(source: AsmAssignSource,
                             targets: List<AsmAssignTarget>,
                             memsizer: IMemSizer,
                             position: Position): AsmAssignmentBase(source, targets, memsizer, position)

internal class AsmAugmentedAssignment(source: AsmAssignSource,
                                      val operator: String,
                                      target: AsmAssignTarget,
                                      memsizer: IMemSizer,
                                      position: Position): AsmAssignmentBase(source, listOf(target), memsizer, position)

