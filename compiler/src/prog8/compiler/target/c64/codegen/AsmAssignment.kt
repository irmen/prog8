package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.DirectMemoryWrite
import prog8.compiler.AssemblyError


enum class AsmTargetStorageType {
    VARIABLE,
    ARRAY,
    MEMORY,
    REGISTER,
    STACK
}

enum class AsmSourceStorageType {
    LITERALNUMBER,
    VARIABLE,
    ARRAY,
    MEMORY,
    REGISTER,
    STACK,              // value is already present on stack
    EXPRESSION,         // expression still to be evaluated
}

internal class AsmAssignTarget(val type: AsmTargetStorageType,
                               program: Program,
                               asmgen: AsmGen,
                               val datatype: DataType,
                               val astVariable: IdentifierReference?,
                               val astArray: ArrayIndexedExpression?,
                               val astMemory: DirectMemoryWrite?,
                               val register: RegisterOrPair?,
                               val origAstTarget: AssignTarget?,        // TODO get rid of this eventually?
                                )
{
    val constMemoryAddress by lazy { astMemory?.addressExpression?.constValue(program)?.number?.toInt() ?: 0}
    val constArrayIndexValue by lazy { astArray?.arrayspec?.size() ?: 0 }
    val vardecl by lazy { astVariable?.targetVarDecl(program.namespace)!! }
    val asmName by lazy {
        if(astVariable!=null)
            asmgen.asmIdentifierName(astVariable)
        else
            asmgen.asmIdentifierName(astArray!!.identifier)
    }

    lateinit var origAssign: AsmAssignment

    init {
        if(astVariable!=null && vardecl.type == VarDeclType.CONST)
            throw AssemblyError("can't assign to a constant")
        if(register!=null && datatype !in IntegerDatatypes)
            throw AssemblyError("register must be integer type")
    }

    companion object {
        fun fromAstAssignment(assign: Assignment, program: Program, asmgen: AsmGen): AsmAssignTarget = with(assign.target) {
            val dt = inferType(program, assign).typeOrElse(DataType.STRUCT)
            when {
                identifier != null -> AsmAssignTarget(AsmTargetStorageType.VARIABLE, program, asmgen, dt, identifier, null, null, null, this)
                arrayindexed != null -> AsmAssignTarget(AsmTargetStorageType.ARRAY, program, asmgen, dt, null, arrayindexed, null, null, this)
                memoryAddress != null -> AsmAssignTarget(AsmTargetStorageType.MEMORY, program, asmgen, dt, null, null, memoryAddress, null, this)
                else -> throw AssemblyError("weird target")
            }
        }
    }
}

internal class AsmAssignSource(val type: AsmSourceStorageType,
                               private val program: Program,
                               val datatype: DataType,
                               val astVariable: IdentifierReference? = null,
                               val astArray: ArrayIndexedExpression? = null,
                               val astMemory: DirectMemoryRead? = null,
                               val register: CpuRegister? = null,
                               val numLitval: NumericLiteralValue? = null,
                               val astExpression: Expression? = null
)
{
    val constMemoryAddress by lazy { astMemory?.addressExpression?.constValue(program)?.number?.toInt() ?: 0}
    val constArrayIndexValue by lazy { astArray?.arrayspec?.size() ?: 0 }
    val vardecl by lazy { astVariable?.targetVarDecl(program.namespace)!! }

    companion object {
        fun fromAstSource(value: Expression, program: Program): AsmAssignSource {
            val cv = value.constValue(program)
            if(cv!=null)
                return AsmAssignSource(AsmSourceStorageType.LITERALNUMBER, program, cv.type, numLitval = cv)

            return when(value) {
                is NumericLiteralValue -> AsmAssignSource(AsmSourceStorageType.LITERALNUMBER, program, value.type, numLitval = cv)
                is StringLiteralValue -> throw AssemblyError("string literal value should not occur anymore for asm generation")
                is ArrayLiteralValue -> throw AssemblyError("array literal value should not occur anymore for asm generation")
                is IdentifierReference -> {
                    val dt = value.inferType(program).typeOrElse(DataType.STRUCT)
                    AsmAssignSource(AsmSourceStorageType.VARIABLE, program, dt, astVariable = value)
                }
                is DirectMemoryRead -> {
                    AsmAssignSource(AsmSourceStorageType.MEMORY, program, DataType.UBYTE, astMemory = value)
                }
                is ArrayIndexedExpression -> {
                    val dt = value.inferType(program).typeOrElse(DataType.STRUCT)
                    AsmAssignSource(AsmSourceStorageType.ARRAY, program, dt, astArray = value)
                }
                else -> {
                    val dt = value.inferType(program).typeOrElse(DataType.STRUCT)
                    AsmAssignSource(AsmSourceStorageType.EXPRESSION, program, dt, astExpression = value)
                }
            }
        }
    }

    fun getAstValue(): Expression = when(type) {
        AsmSourceStorageType.LITERALNUMBER -> numLitval!!
        AsmSourceStorageType.VARIABLE -> astVariable!!
        AsmSourceStorageType.ARRAY -> astArray!!
        AsmSourceStorageType.MEMORY -> astMemory!!
        AsmSourceStorageType.EXPRESSION -> astExpression!!
        AsmSourceStorageType.REGISTER -> TODO()
        AsmSourceStorageType.STACK -> TODO()
    }

    fun withAdjustedDt(newType: DataType) =
            AsmAssignSource(type, program, newType, astVariable, astArray, astMemory, register, numLitval, astExpression)

}


internal class AsmAssignment(val source: AsmAssignSource,
                             val target: AsmAssignTarget,
                             val isAugmentable: Boolean,
                             val position: Position) {

    init {
        require(source.datatype==target.datatype) {"source and target datatype must be identical"}
    }
}
