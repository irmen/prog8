package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.INTERNED_STRINGS_MODULENAME
import prog8.code.core.*

internal class VerifyFunctionArgTypes(val program: Program, val options: CompilationOptions, val errors: IErrorReporter) : IAstVisitor {

    override fun visit(program: Program) {
        super.visit(program)

        // detect invalid (double) memory slabs
        for(slab in memorySlabs) {
            val other = memorySlabs.first { it.name==slab.name }
            if(other!==slab && (other.size!=slab.size || other.align!=slab.align)) {
                errors.err("memory block '${slab.name}' already exists with a different size and/or alignment at ${other.position}", slab.position)
            }
        }

        // remove unused strings from interned strings block
        val internedBlock = program.allBlocks.singleOrNull { it.name == INTERNED_STRINGS_MODULENAME }
        internedBlock?.statements?.withIndex()?.reversed()?.forEach { (index, st) ->
            if(st is VarDecl && st.scopedName !in allStringRefs) {
                internedBlock.statements.removeAt(index)
            }
        }
    }

    override fun visit(identifier: IdentifierReference) {
        if(identifier.wasStringLiteral()) {
            allStringRefs.add(identifier.nameInSource)
        }
    }

    private val allStringRefs = mutableListOf<List<String>>()

    private class Slab(val name: String, val size: Int, val align: Int, val position: Position)
    private val memorySlabs = mutableListOf<Slab>()

    override fun visit(functionCallExpr: FunctionCallExpression) {
        val error = checkTypes(functionCallExpr as IFunctionCall, program)
        if(error!=null)
            errors.err(error.first, error.second)
        else {
            if(functionCallExpr.target.nameInSource==listOf("memory")) {
                val name = (functionCallExpr.args[0] as StringLiteral).value
                val size = (functionCallExpr.args[1] as? NumericLiteral)?.number?.toInt()
                val align = (functionCallExpr.args[2] as? NumericLiteral)?.number?.toInt()
                if(size==null)
                    errors.err("argument must be a constant", functionCallExpr.args[1].position)
                if(align==null)
                    errors.err("argument must be a constant", functionCallExpr.args[2].position)
                if(size!=null && align!=null)
                    memorySlabs.add(Slab(name, size, align, functionCallExpr.position))
            }
        }

        super.visit(functionCallExpr)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val error = checkTypes(functionCallStatement as IFunctionCall, program)
        if(error!=null)
            errors.err(error.first, error.second)

        super.visit(functionCallStatement)
    }

    override fun visit(decl: VarDecl) {
        if(options.zeropage==ZeropageType.DONTUSE && decl.zeropage == ZeropageWish.REQUIRE_ZEROPAGE)
            errors.err("zeropage usage has been disabled by options", decl.position)

        super.visit(decl)
    }

    companion object {

        private fun argTypeCompatible(argDt: DataType, paramDt: DataType): Boolean {
            if(argDt==paramDt)
                return true

            // there are some exceptions that are considered compatible, such as STR <> UWORD,  UWORD <> pointer
            if(argDt.isUnsignedWord)
                if((paramDt.isString || paramDt.isUnsignedByteArray || paramDt.isPointer))
                    return true
            if(argDt.isString) {
                if(paramDt.isUnsignedWord || paramDt.isUnsignedByteArray)
                    return true
                if(paramDt.isPointer && paramDt.sub==BaseDataType.UBYTE)
                    return true
            }

            // if uword is passed, check if the parameter type is pointer to array element type
            if(argDt.isArray && paramDt.isPointer) {
                TODO("array vs element pointer check")
            }

            return false
        }

        fun checkTypes(call: IFunctionCall, program: Program): Pair<String, Position>? {
            val argITypes = call.args.map { it.inferType(program) }
            val firstUnknownDt = argITypes.indexOfFirst { it.isUnknown }
            if(firstUnknownDt>=0) {
                // if an uword is expected but a pointer is provided, that is okay without a cast
                val identifier = call.args[0] as? IdentifierReference
                return if(identifier==null || identifier.targetStatement(program)!=null)
                    Pair("argument ${firstUnknownDt + 1} invalid argument type", call.args[firstUnknownDt].position)
                else
                    null
            }
            val argtypes = argITypes.map { it.getOrUndef() }
            val target = call.target.targetStatement(program)
            if (target is Subroutine) {
                val consideredParamTypes: List<DataType> = target.parameters.map { it.type }
                if(argtypes.size != consideredParamTypes.size)
                    return Pair("invalid number of arguments", call.position)
                val mismatch = argtypes.zip(consideredParamTypes).indexOfFirst { !argTypeCompatible(it.first, it.second) }
                if(mismatch>=0) {
                    val actual = argtypes[mismatch]
                    val expected = consideredParamTypes[mismatch]
                    return Pair("argument ${mismatch + 1} type mismatch, was: $actual expected: $expected", call.args[mismatch].position)
                }
                if(target.isAsmSubroutine) {
                    if(target.asmReturnvaluesRegisters.size>1) {
                        // multiple return values will NOT work inside an expression. Use an assignment first.
                        val parent = if(call is Statement) call.parent else if(call is Expression) call.parent else null
                        if (call !is FunctionCallStatement) {
                            val checkParent =
                                if(parent is TypecastExpression)
                                    parent.parent
                                else
                                    parent
                            if (checkParent !is Assignment && checkParent !is VarDecl) {
                                if (target.asmReturnvaluesRegisters.size>1) {
                                    return Pair("can't use subroutine call that returns multiple return values here", call.position)
                                }
                            }
                        }
                    }
                }
            }
            else if (target is BuiltinFunctionPlaceholder) {
                val func = BuiltinFunctions.getValue(target.name)
                val consideredParamTypes = func.parameters.map { it.possibleDatatypes }
                if(argtypes.size != consideredParamTypes.size)
                    return Pair("invalid number of arguments", call.position)
                argtypes.zip(consideredParamTypes).forEachIndexed { index, pair ->
                    val anyCompatible = pair.second.any {
                        if(it.isArray)
                            pair.first.isArray
                        else
                            argTypeCompatible(pair.first, DataType.forDt(it))
                    }
                    if (!anyCompatible) {
                        val actual = pair.first
                        return if(pair.second.size==1) {
                            val expected = pair.second[0]
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
}
