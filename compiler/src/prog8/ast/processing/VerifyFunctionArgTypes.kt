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

    override fun visit(functionCall: FunctionCall)
            = checkTypes(functionCall as IFunctionCall, functionCall.definingScope())

    override fun visit(functionCallStatement: FunctionCallStatement)
            = checkTypes(functionCallStatement as IFunctionCall, functionCallStatement.definingScope())

    private fun checkTypes(call: IFunctionCall, scope: INameScope) {
        val argtypes = call.args.map { it.inferType(program).typeOrElse(DataType.STRUCT) }
        val target = call.target.targetStatement(scope)
        when(target) {
            is Subroutine -> {
                val paramtypes = target.parameters.map { it.type }
                if(argtypes!=paramtypes)
                    throw CompilerException("parameter type mismatch $call")
            }
            is BuiltinFunctionStatementPlaceholder -> {
                val func = BuiltinFunctions.getValue(target.name)
                val paramtypes = func.parameters.map { it.possibleDatatypes }
                for(x in argtypes.zip(paramtypes)) {
                    if(x.first !in x.second)
                        throw CompilerException("parameter type mismatch $call")
                }
            }
            else -> {}
        }
    }
}
