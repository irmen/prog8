package prog8.codegen.cpu6502.assignment

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal
import prog8.codegen.cpu6502.returnsWhatWhere


internal enum class TargetStorageKind {
    VARIABLE,
    ARRAY,
    MEMORY,
    REGISTER
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
                               val origAstTarget: PtAssignTarget? = null
                               )
{
    val constArrayIndexValue by lazy { array?.index?.asConstInteger()?.toUInt() }
    val asmVarname: String by lazy {
        if (array == null)
            variableAsmName!!
        else
            asmgen.asmVariableName(array.variable)
    }

    init {
        if(register!=null && datatype !in NumericDatatypes)
            throw AssemblyError("register must be integer or float type")
    }

    companion object {
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
                    else -> throw AssemblyError("weird target")
                }
            }
        }

        fun fromRegisters(registers: RegisterOrPair, signed: Boolean, pos: Position, scope: IPtSubroutine?, asmgen: AsmGen6502Internal): AsmAssignTarget =
                when(registers) {
                    RegisterOrPair.A,
                    RegisterOrPair.X,
                    RegisterOrPair.Y -> AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, if(signed) DataType.BYTE else DataType.UBYTE, scope, pos, register = registers)
                    RegisterOrPair.AX,
                    RegisterOrPair.AY,
                    RegisterOrPair.XY -> AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, if(signed) DataType.WORD else DataType.UWORD, scope, pos, register = registers)
                    RegisterOrPair.FAC1,
                    RegisterOrPair.FAC2 -> AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, DataType.FLOAT, scope, pos, register = registers)
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
                    RegisterOrPair.R15 -> AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, if(signed) DataType.WORD else DataType.UWORD, scope, pos, register = registers)
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
                left is PtArrayIndexer && left isSameAs array!! && left.splitWords==array.splitWords
            }
            TargetStorageKind.MEMORY -> {
                left isSameAs memory!!
            }
            TargetStorageKind.REGISTER -> {
                false
            }
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
        else
            asmgen.asmVariableName(array.variable)

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
                    if(value.type == DataType.UWORD && varName.lowercase().startsWith("cx16.r")) {
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
                    val returnType = sub.returnsWhatWhere().firstOrNull { rr -> rr.first.registerOrPair != null || rr.first.statusflag!=null }?.second
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
            if(target.datatype in ByteDatatypes && datatype in ByteDatatypes) {
                return withAdjustedDt(target.datatype)
            } else if(target.datatype in WordDatatypes && datatype in WordDatatypes) {
                return withAdjustedDt(target.datatype)
            }
        }
        return this
    }

}


internal sealed class AsmAssignmentBase(val source: AsmAssignSource,
                                        val target: AsmAssignTarget,
                                        val memsizer: IMemSizer,
                                        val position: Position) {
    init {
        if(target.register !in arrayOf(RegisterOrPair.XY, RegisterOrPair.AX, RegisterOrPair.AY))
            require(source.datatype != DataType.UNDEFINED) { "must not be placeholder/undefined datatype at $position" }
            require(memsizer.memorySize(source.datatype) <= memsizer.memorySize(target.datatype)) {
                "source dt size must be less or equal to target dt size at $position srcdt=${source.datatype} targetdt=${target.datatype}"
            }
    }
}

internal class AsmAssignment(source: AsmAssignSource,
                             target: AsmAssignTarget,
                             memsizer: IMemSizer,
                             position: Position): AsmAssignmentBase(source, target, memsizer, position)

internal class AsmAugmentedAssignment(source: AsmAssignSource,
                                      val operator: String,
                                      target: AsmAssignTarget,
                                      memsizer: IMemSizer,
                                      position: Position): AsmAssignmentBase(source, target, memsizer, position)

