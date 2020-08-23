package prog8.compiler.target.c64.codegen.assignment

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.DirectMemoryWrite
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.codegen.AsmGen


internal enum class TargetStorageKind {
    VARIABLE,
    ARRAY,
    MEMORY,
    REGISTER,
    STACK
}

internal enum class SourceStorageKind {
    LITERALNUMBER,
    VARIABLE,
    ARRAY,
    MEMORY,
    REGISTER,
    STACK,              // value is already present on stack
    EXPRESSION,         // expression in ast-form, still to be evaluated
}

internal class AsmAssignTarget(val kind: TargetStorageKind,
                               program: Program,
                               asmgen: AsmGen,
                               val datatype: DataType,
                               val variable: IdentifierReference?,
                               val array: ArrayIndexedExpression?,
                               val memory: DirectMemoryWrite?,
                               val register: RegisterOrPair?,
                               val origAstTarget: AssignTarget?,        // TODO get rid of this eventually?
                                )
{
    val constMemoryAddress by lazy { memory?.addressExpression?.constValue(program)?.number?.toInt() ?: 0}
    val constArrayIndexValue by lazy { array?.arrayspec?.constIndex() }
    val vardecl by lazy { variable?.targetVarDecl(program.namespace)!! }
    val asmName by lazy {
        if(variable!=null)
            asmgen.asmIdentifierName(variable)
        else
            asmgen.asmIdentifierName(array!!.identifier)
    }

    lateinit var origAssign: AsmAssignment

    init {
        if(variable!=null && vardecl.type == VarDeclType.CONST)
            throw AssemblyError("can't assign to a constant")
        if(register!=null && datatype !in IntegerDatatypes)
            throw AssemblyError("register must be integer type")
    }

    companion object {
        fun fromAstAssignment(assign: Assignment, program: Program, asmgen: AsmGen): AsmAssignTarget = with(assign.target) {
            val dt = inferType(program, assign).typeOrElse(DataType.STRUCT)
            when {
                identifier != null -> AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, dt, identifier, null, null, null, this)
                arrayindexed != null -> AsmAssignTarget(TargetStorageKind.ARRAY, program, asmgen, dt, null, arrayindexed, null, null, this)
                memoryAddress != null -> AsmAssignTarget(TargetStorageKind.MEMORY, program, asmgen, dt, null, null, memoryAddress, null, this)
                else -> throw AssemblyError("weird target")
            }
        }
    }
}

internal class AsmAssignSource(val kind: SourceStorageKind,
                               private val program: Program,
                               val datatype: DataType,
                               val variable: IdentifierReference? = null,
                               val array: ArrayIndexedExpression? = null,
                               val memory: DirectMemoryRead? = null,
                               val register: CpuRegister? = null,
                               val number: NumericLiteralValue? = null,
                               val expression: Expression? = null
)
{
    val constMemoryAddress by lazy { memory?.addressExpression?.constValue(program)?.number?.toInt() ?: 0}
    val constArrayIndexValue by lazy { array?.arrayspec?.constIndex() }
    val vardecl by lazy { variable?.targetVarDecl(program.namespace)!! }

    companion object {
        fun fromAstSource(value: Expression, program: Program): AsmAssignSource {
            val cv = value.constValue(program)
            if(cv!=null)
                return AsmAssignSource(SourceStorageKind.LITERALNUMBER, program, cv.type, number = cv)

            return when(value) {
                is NumericLiteralValue -> AsmAssignSource(SourceStorageKind.LITERALNUMBER, program, value.type, number = cv)
                is StringLiteralValue -> throw AssemblyError("string literal value should not occur anymore for asm generation")
                is ArrayLiteralValue -> throw AssemblyError("array literal value should not occur anymore for asm generation")
                is IdentifierReference -> {
                    val dt = value.inferType(program).typeOrElse(DataType.STRUCT)
                    AsmAssignSource(SourceStorageKind.VARIABLE, program, dt, variable = value)
                }
                is DirectMemoryRead -> {
                    AsmAssignSource(SourceStorageKind.MEMORY, program, DataType.UBYTE, memory = value)
                }
                is ArrayIndexedExpression -> {
                    val dt = value.inferType(program).typeOrElse(DataType.STRUCT)
                    AsmAssignSource(SourceStorageKind.ARRAY, program, dt, array = value)
                }
                else -> {
                    val dt = value.inferType(program).typeOrElse(DataType.STRUCT)
                    AsmAssignSource(SourceStorageKind.EXPRESSION, program, dt, expression = value)
                }
            }
        }
    }

    fun getAstValue(): Expression = when(kind) {
        SourceStorageKind.LITERALNUMBER -> number!!
        SourceStorageKind.VARIABLE -> variable!!
        SourceStorageKind.ARRAY -> array!!
        SourceStorageKind.MEMORY -> memory!!
        SourceStorageKind.EXPRESSION -> expression!!
        SourceStorageKind.REGISTER -> TODO()
        SourceStorageKind.STACK -> TODO()
    }

    fun withAdjustedDt(newType: DataType) =
            AsmAssignSource(kind, program, newType, variable, array, memory, register, number, expression)

}


internal class AsmAssignment(val source: AsmAssignSource,
                             val target: AsmAssignTarget,
                             val isAugmentable: Boolean,
                             val position: Position) {

    init {
        require(source.datatype==target.datatype) {"source and target datatype must be identical"}
    }
}
