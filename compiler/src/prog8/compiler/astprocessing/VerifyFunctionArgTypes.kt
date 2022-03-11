package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.Expression
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.expressions.PipeExpression
import prog8.ast.expressions.TypecastExpression
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter
import prog8.code.core.Position
import prog8.compiler.BuiltinFunctions
import prog8.compiler.builtinFunctionReturnType

internal class VerifyFunctionArgTypes(val program: Program, val errors: IErrorReporter) : IAstVisitor {

    override fun visit(functionCallExpr: FunctionCallExpression) {
        val error = checkTypes(functionCallExpr as IFunctionCall, program)
        if(error!=null)
            errors.err(error.first, error.second)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val error = checkTypes(functionCallStatement as IFunctionCall, program)
        if(error!=null)
            errors.err(error.first, error.second)
    }

    companion object {

        private fun argTypeCompatible(argDt: DataType, paramDt: DataType): Boolean {
            if(argDt==paramDt)
                return true

            // there are some exceptions that are considered compatible, such as STR <> UWORD
            if(argDt==DataType.STR && paramDt==DataType.UWORD ||
                    argDt==DataType.UWORD && paramDt==DataType.STR ||
                    argDt==DataType.UWORD && paramDt==DataType.ARRAY_UB ||
                    argDt==DataType.STR && paramDt==DataType.ARRAY_UB)
                return true

            return false
        }

        fun checkTypes(call: IFunctionCall, program: Program): Pair<String, Position>? {
            val argITypes = call.args.map { it.inferType(program) }
            val firstUnknownDt = argITypes.indexOfFirst { it.isUnknown }
            if(firstUnknownDt>=0)
                return Pair("argument ${firstUnknownDt+1} invalid argument type", call.args[firstUnknownDt].position)
            val argtypes = argITypes.map { it.getOr(DataType.UNDEFINED) }
            val target = call.target.targetStatement(program)
            val isPartOfPipeSegments = (call.parent as? IPipe)?.segments?.contains(call as Node) == true
            val errormessageAboutArgs = if(isPartOfPipeSegments) "invalid number of arguments in piped call" else "invalid number of arguments"
            if (target is Subroutine) {
                val consideredParamTypes: List<DataType> = if(isPartOfPipeSegments) {
                    target.parameters.drop(1).map { it.type }    // skip first one (the implicit first arg), this is checked elsewhere
                } else {
                    target.parameters.map { it.type }
                }
                if(argtypes.size != consideredParamTypes.size)
                    return Pair(errormessageAboutArgs, call.position)
                val mismatch = argtypes.zip(consideredParamTypes).indexOfFirst { !argTypeCompatible(it.first, it.second) }
                if(mismatch>=0) {
                    val actual = argtypes[mismatch].toString()
                    val expected = consideredParamTypes[mismatch].toString()
                    return Pair("argument ${mismatch + 1} type mismatch, was: $actual expected: $expected", call.args[mismatch].position)
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
                                return Pair("can't use subroutine call that returns multiple return values here", call.position)
                            }
                        }
                    }
                }
            }
            else if (target is BuiltinFunctionPlaceholder) {
                val func = BuiltinFunctions.getValue(target.name)
                val consideredParamTypes: List<Array<DataType>> = if(isPartOfPipeSegments) {
                    func.parameters.drop(1).map { it.possibleDatatypes }    // skip first one (the implicit first arg), this is checked elsewhere
                } else {
                    func.parameters.map { it.possibleDatatypes }
                }
                if(argtypes.size != consideredParamTypes.size)
                    return Pair(errormessageAboutArgs, call.position)
                argtypes.zip(consideredParamTypes).forEachIndexed { index, pair ->
                    val anyCompatible = pair.second.any { argTypeCompatible(pair.first, it) }
                    if (!anyCompatible) {
                        val actual = pair.first.toString()
                        return if(pair.second.size==1) {
                            val expected = pair.second[0].toString()
                            Pair("argument ${index + 1} type mismatch, was: $actual expected: $expected", call.args[index].position)
                        } else {
                            val expected = pair.second.toList().toString()
                            Pair("argument ${index + 1} type mismatch, was: $actual expected one of: $expected", call.args[index].position)
                        }
                    }
                }
            }

            return null
        }
    }

    override fun visit(pipe: PipeExpression) {
        processPipe(pipe.source, pipe.segments, pipe)
        if(errors.noErrors()) {
            val last = (pipe.segments.last() as IFunctionCall).target
            when (val target = last.targetStatement(program)!!) {
                is BuiltinFunctionPlaceholder -> {
                    if (!BuiltinFunctions.getValue(target.name).hasReturn)
                        errors.err("invalid pipe expression; last term doesn't return a value", last.position)
                }
                is Subroutine -> {
                    if (target.returntypes.isEmpty())
                        errors.err("invalid pipe expression; last term doesn't return a value", last.position)
                    else if (target.returntypes.size != 1)
                        errors.err("invalid pipe expression; last term doesn't return a single value", last.position)
                }
                else -> errors.err("invalid pipe expression; last term doesn't return a value", last.position)
            }
            super.visit(pipe)
        }
    }

    override fun visit(pipe: Pipe) {
        processPipe(pipe.source, pipe.segments, pipe)
        if(errors.noErrors()) {
            super.visit(pipe)
        }
    }

    private fun processPipe(source: Expression, segments: List<Expression>, scope: Node) {

        val sourceArg = (source as? IFunctionCall)?.args?.firstOrNull()
        if(sourceArg!=null && segments.any { (it as IFunctionCall).args.firstOrNull() === sourceArg})
            throw FatalAstException("some pipe segment first arg is replicated from the source functioncall arg")

        // invalid size and other issues will be handled by the ast checker later.
        var valueDt = source.inferType(program).getOrElse {
            throw FatalAstException("invalid dt")
        }

        for(funccall in segments) {
            val target = (funccall as IFunctionCall).target.targetStatement(program)
            if(target!=null) {
                when (target) {
                    is BuiltinFunctionPlaceholder -> {
                        val func = BuiltinFunctions.getValue(target.name)
                        if(func.parameters.size!=1)
                            errors.err("can only use unary function", funccall.position)
                        else if(!func.hasReturn && funccall !== segments.last())
                            errors.err("function must return a single value", funccall.position)

                        val paramDts = func.parameters.firstOrNull()?.possibleDatatypes
                        if(paramDts!=null && !paramDts.any { valueDt isAssignableTo it })
                            errors.err("pipe value datatype $valueDt incompatible with function argument ${paramDts.toList()}", funccall.position)

                        if(errors.noErrors()) {
                            // type can depend on the argument(s) of the function. For now, we only deal with unary functions,
                            // so we know there must be a single argument. Take its type from the previous expression in the pipe chain.
                            val zero = defaultZero(valueDt, funccall.position)
                            valueDt = builtinFunctionReturnType(func.name, listOf(zero), program).getOrElse { DataType.UNDEFINED }
                        }
                    }
                    is Subroutine -> {
                        if(target.parameters.size!=1)
                            errors.err("can only use unary function", funccall.position)
                        else if(target.returntypes.size!=1 && funccall !== segments.last())
                            errors.err("function must return a single value", funccall.position)

                        val paramDt = target.parameters.firstOrNull()?.type
                        if(paramDt!=null && !(valueDt isAssignableTo paramDt))
                            errors.err("pipe value datatype $valueDt incompatible with function argument $paramDt", funccall.position)

                        if(target.returntypes.isNotEmpty())
                            valueDt = target.returntypes.single()
                    }
                    is VarDecl -> {
                        if(!(valueDt isAssignableTo target.datatype))
                            errors.err("final pipe value datatype can't be stored in pipe ending variable", funccall.position)
                    }
                    else -> {
                        throw FatalAstException("weird function")
                    }
                }
            }
        }
    }

}
