package prog8.ast.processing

import prog8.ast.IFunctionCall
import prog8.ast.INameScope
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.expressions.FunctionCall
import prog8.ast.statements.BuiltinFunctionStatementPlaceholder
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.statements.Subroutine
import prog8.compiler.CompilerException
import prog8.functions.BuiltinFunctions

class VerifyFunctionArgTypes(val program: Program) : IAstVisitor {

    override fun visit(functionCall: FunctionCall) {
        val error = checkTypes(functionCall as IFunctionCall, functionCall.definingScope(), program)
        if(error!=null)
            throw CompilerException(error)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val error = checkTypes(functionCallStatement as IFunctionCall, functionCallStatement.definingScope(), program)
        if (error!=null)
            throw CompilerException(error)
    }

    companion object {
        fun checkTypes(call: IFunctionCall, scope: INameScope, program: Program): String? {
            val argtypes = call.args.map { it.inferType(program).typeOrElse(DataType.STRUCT) }
            val target = call.target.targetStatement(scope)
            if (target is Subroutine) {
                // asmsub types are not checked specifically at this time
                if(call.args.size != target.parameters.size)
                    return "invalid number of arguments"
                val paramtypes = target.parameters.map { it.type }
                val mismatch = argtypes.zip(paramtypes).indexOfFirst { it.first != it.second}
                if(mismatch>=0) {
                    val actual = argtypes[mismatch].toString()
                    val expected = paramtypes[mismatch].toString()
                    return "argument ${mismatch + 1} type mismatch, was: $actual expected: $expected"
                }
            }
            else if (target is BuiltinFunctionStatementPlaceholder) {
                val func = BuiltinFunctions.getValue(target.name)
                if(call.args.size != func.parameters.size)
                    return "invalid number of arguments"
                val paramtypes = func.parameters.map { it.possibleDatatypes }
                for (x in argtypes.zip(paramtypes).withIndex()) {
                    if (x.value.first !in x.value.second) {
                        val actual = x.value.first.toString()
                        val expected = x.value.second.toString()
                        return "argument ${x.index + 1} type mismatch, was: $actual expected: $expected"
                    }
                }
            }

            return null
        }
    }
}
