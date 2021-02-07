package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.expressions.Expression
import prog8.ast.expressions.FunctionCall
import prog8.ast.expressions.TypecastExpression
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.compiler.CompilerException
import prog8.compiler.functions.BuiltinFunctions

class VerifyFunctionArgTypes(val program: Program) : IAstVisitor {

    override fun visit(functionCall: FunctionCall) {
        val error = checkTypes(functionCall as IFunctionCall, program)
        if(error!=null)
            throw CompilerException(error)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val error = checkTypes(functionCallStatement as IFunctionCall, program)
        if (error!=null)
            throw CompilerException(error)
    }

    companion object {

        private fun argTypeCompatible(argDt: DataType, paramDt: DataType): Boolean {
            if(argDt==paramDt)
                return true

            // there are some exceptions that are considered compatible, such as STR <> UWORD
            if(argDt==DataType.STR && paramDt==DataType.UWORD ||
                    argDt==DataType.UWORD && paramDt==DataType.STR)
                return true

            return false
        }

        fun checkTypes(call: IFunctionCall, program: Program): String? {
            val argITypes = call.args.map { it.inferType(program) }
            val firstUnknownDt = argITypes.indexOfFirst { it.isUnknown }
            if(firstUnknownDt>=0)
                return "argument ${firstUnknownDt+1} invalid argument type"
            val argtypes = argITypes.map { it.typeOrElse(DataType.STRUCT) }
            val target = call.target.targetStatement(program)
            if (target is Subroutine) {
                if(call.args.size != target.parameters.size)
                    return "invalid number of arguments"
                val paramtypes = target.parameters.map { it.type }
                val mismatch = argtypes.zip(paramtypes).indexOfFirst { !argTypeCompatible(it.first, it.second) }
                if(mismatch>=0) {
                    val actual = argtypes[mismatch].toString()
                    val expected = paramtypes[mismatch].toString()
                    return "argument ${mismatch + 1} type mismatch, was: $actual expected: $expected"
                }
                if(target.isAsmSubroutine) {
                    if(target.asmReturnvaluesRegisters.size>1) {
                        // multiple return values will NOT work inside an expression.
                        // they MIGHT work in a regular assignment or just a function call statement.
                        val parent = if(call is Statement) call.parent else if(call is Expression) call.parent else null
                        if (call !is FunctionCallStatement) {
                            val checkParent =
                                if(parent is TypecastExpression)
                                    parent.parent
                                else
                                    parent
                            if (checkParent !is Assignment && checkParent !is VarDecl) {
                                return "can't use subroutine call that returns multiple return values here"
                            }
                        }
                    }
                }
            }
            else if (target is BuiltinFunctionStatementPlaceholder) {
                val func = BuiltinFunctions.getValue(target.name)
                if(call.args.size != func.parameters.size)
                    return "invalid number of arguments"
                val paramtypes = func.parameters.map { it.possibleDatatypes }
                argtypes.zip(paramtypes).forEachIndexed { index, pair ->
                    val anyCompatible = pair.second.any { argTypeCompatible(pair.first, it) }
                    if (!anyCompatible) {
                        val actual = pair.first.toString()
                        val expected = pair.second.toString()
                        return "argument ${index + 1} type mismatch, was: $actual expected: $expected"
                    }
                }
            }

            return null
        }
    }
}
