package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import prog8.code.target.C64Target


class AstPreprocessor(val program: Program,
                      val errors: IErrorReporter,
                      val options: CompilationOptions) : AstWalker() {

    override fun before(program: Program): Iterable<IAstModification> {
        if(options.compTarget.name==C64Target.NAME) {
            if(options.zeropage==ZeropageType.KERNALSAFE || options.zeropage==ZeropageType.FULL) {
                // there is enough space in the zero page to put the cx16 virtual registers there.
                // unfortunately, can't be the same address as CommanderX16.
                relocateCx16VirtualRegisters(program, 0x0004u)
            }
        }
        return noModifications
    }

    private fun relocateCx16VirtualRegisters(program: Program, baseAddress: UInt) {
        val cx16block = program.allBlocks.single { it.name == "cx16" }
        val memVars = cx16block.statements
            .filterIsInstance<VarDecl>()
            .associateBy { it.name }

        for (regnum in 0u..15u) {
            val rX = memVars.getValue("r$regnum")
            val rXL = memVars.getValue("r${regnum}L")
            val rXH = memVars.getValue("r${regnum}H")
            val rXs = memVars.getValue("r${regnum}s")
            val rXsL = memVars.getValue("r${regnum}sL")
            val rXsH = memVars.getValue("r${regnum}sH")
            setAddress(rX, baseAddress + 2u * regnum)
            setAddress(rXL, baseAddress + 2u * regnum)
            setAddress(rXH, baseAddress + 2u * regnum + 1u)
            setAddress(rXs, baseAddress + 2u * regnum)
            setAddress(rXsL, baseAddress + 2u * regnum)
            setAddress(rXsH, baseAddress + 2u * regnum + 1u)
        }
    }

    private fun setAddress(vardecl: VarDecl, address: UInt) {
        val oldAddr = vardecl.value as NumericLiteral
        vardecl.value = NumericLiteral(oldAddr.type, address.toDouble(), oldAddr.position)
    }

    override fun before(directive: Directive, parent: Node): Iterable<IAstModification> {
        if(directive.parent is Expression)
            errors.err("${directive.directive} is ambiguous here as an operand for the % operator and a directive. Add spaces around the operator % to distinguish it.", directive.position)
        return noModifications
    }

    override fun before(char: CharLiteral, parent: Node): Iterable<IAstModification> {
        if(char.encoding== Encoding.DEFAULT)
            char.encoding = char.definingModule.textEncoding
        return noModifications
    }

    override fun before(string: StringLiteral, parent: Node): Iterable<IAstModification> {
        if(string.encoding==Encoding.DEFAULT)
            string.encoding = string.definingModule.textEncoding
        return super.before(string, parent)
    }

    override fun after(range: RangeExpression, parent: Node): Iterable<IAstModification> {
        // has to be done before the constant folding, otherwise certain checks there will fail on invalid range sizes
        val modifications = mutableListOf<IAstModification>()
        if(range.from !is NumericLiteral) {
            try {
                val constval = range.from.constValue(program)
                if (constval != null)
                    modifications += IAstModification.ReplaceNode(range.from, constval, range)
            } catch (_: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        if(range.to !is NumericLiteral) {
            try {
                val constval = range.to.constValue(program)
                if(constval!=null)
                    modifications += IAstModification.ReplaceNode(range.to, constval, range)
            } catch (_: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        if(range.step !is NumericLiteral) {
            try {
                val constval = range.step.constValue(program)
                if(constval!=null)
                    modifications += IAstModification.ReplaceNode(range.step, constval, range)
            } catch (_: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        return modifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {

        // move vardecls in Anonymous scope up to the containing subroutine
        // and add initialization assignment in its place if needed
        val vars = scope.statements.asSequence().filterIsInstance<VarDecl>()
        val parentscope = scope.definingScope
        if(vars.any() && parentscope !== parent) {
            val movements = mutableListOf<IAstModification>()
            val replacements = mutableListOf<IAstModification>()

            for(decl in vars) {
                if(shouldUnSplitArray(decl))
                    continue  // unsplitting must be done first
                if(decl.type != VarDeclType.VAR) {
                    movements.add(IAstModification.InsertFirst(decl, parentscope))
                    replacements.add(IAstModification.Remove(decl, scope))
                } else {
                    val declToInsert: VarDecl
                    if(decl.names.size>1) {
                        // we need to handle multi-decl here too, the desugarer maybe has not processed it here yet...
                        if(decl.value!=null) {
                            decl.names.forEach { name ->
                                val target = AssignTarget(
                                    IdentifierReference(listOf(name), decl.position),
                                    null,
                                    null,
                                    null,
                                    false,
                                    position = decl.position
                                )
                                val assign = Assignment(target.copy(), decl.value!!.copy(), AssignmentOrigin.VARINIT, decl.position)
                                replacements.add(IAstModification.InsertAfter(decl, assign, scope))
                            }
                            replacements.add(IAstModification.Remove(decl, scope))
                            decl.value = null
                            decl.allowInitializeWithZero = false
                            declToInsert = decl
                        } else {
                            // just move it to the defining scope
                            replacements.add(IAstModification.Remove(decl, scope))
                            declToInsert = decl
                        }
                    } else {
                        // handle declaration of a single variable
                        if(decl.value!=null && decl.datatype.isNumericOrBool) {
                            val target = AssignTarget(
                                IdentifierReference(listOf(decl.name), decl.position),
                                null,
                                null,
                                null,
                                false,
                                position = decl.position
                            )
                            val assign = Assignment(target, decl.value!!, AssignmentOrigin.VARINIT, decl.position)
                            replacements.add(IAstModification.ReplaceNode(decl, assign, scope))
                            decl.value = null
                            decl.allowInitializeWithZero = false
                            declToInsert = decl.copy()
                        } else {
                            replacements.add(IAstModification.Remove(decl, scope))
                            declToInsert = decl
                        }
                    }
                    movements.add(IAstModification.InsertFirst(declToInsert, parentscope))
                }
            }
            return movements + replacements
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator=="in") {
            val containment = ContainmentCheck(expr.left, expr.right, expr.position)
            return listOf(IAstModification.ReplaceNode(expr, containment, parent))
        }
        if(expr.operator=="not in") {
            val containment = ContainmentCheck(expr.left, expr.right, expr.position)
            val notContainment = PrefixExpression("not", containment, expr.position)
            return listOf(IAstModification.ReplaceNode(expr, notContainment, parent))
        }
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val nextAssignment = decl.nextSibling() as? Assignment
        if(nextAssignment!=null && nextAssignment.origin!=AssignmentOrigin.VARINIT) {
            // check if the following assignment initializes the variable
            if(decl.value==null && nextAssignment.target.identifier?.targetVarDecl()===decl) {
                if(!nextAssignment.value.referencesIdentifier(nextAssignment.target.identifier!!.nameInSource))
                    nextAssignment.origin = AssignmentOrigin.VARINIT
            }
        }

        if(shouldUnSplitArray(decl)) {
            return makeUnSplitArray(decl)
        }

        // make all subidentifiers (names of structs, basically) fully scoped
        val subident = decl.datatype.subIdentifier
        if(subident!=null && subident.size<2) {
            val struct = decl.definingScope.lookup(subident) as? StructDecl
            if(struct!=null) {
                val newDecl = decl.copy(DataType.pointer(struct.scopedName))
                return listOf(IAstModification.ReplaceNode(decl, newDecl, decl.parent))
            }
        }

        return noModifications
    }

    private fun shouldUnSplitArray(decl: VarDecl): Boolean =
        options.dontSplitWordArrays && decl.datatype.isSplitWordArray

    private fun makeUnSplitArray(decl: VarDecl): Iterable<IAstModification> {
        val splitDt = DataType.arrayFor(decl.datatype.sub!!, false)
        val newDecl = VarDecl(
            decl.type, decl.origin, splitDt, decl.zeropage, decl.splitwordarray, decl.arraysize, decl.name, emptyList(),
            decl.value?.copy(), decl.sharedWithAsm, decl.alignment, false, decl.position
        )
        return listOf(IAstModification.ReplaceNode(decl, newDecl, decl.parent))
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        // For non-kernal subroutines and non-asm parameters:
        // inject subroutine params as local variables (if they're not there yet).
        // If the param should be in a R0-R15 register, don't make a local variable but an alias instead.
        val symbolsInSub = subroutine.allDefinedSymbols
        val namesInSub = symbolsInSub.map{ it.first }.toSet()
        if(subroutine.asmAddress==null) {
            if(!subroutine.isAsmSubroutine && subroutine.parameters.isNotEmpty()) {
                val mods = mutableListOf<IAstModification>()
                val (normalParams, registerParams) = subroutine.parameters.partition { it.registerOrPair==null }
                if(normalParams.isNotEmpty()) {
                    val existingVars = subroutine.statements.asSequence().filterIsInstance<VarDecl>().map { it.name }.toSet()
                    normalParams
                        .filter { it.name !in namesInSub && it.name !in existingVars }
                        .forEach {
                            val vardecl = VarDecl.fromParameter(it)
                            mods += IAstModification.InsertFirst(vardecl, subroutine)
                        }
                }
                if(registerParams.isNotEmpty()) {
                    val existingAliases = subroutine.statements.asSequence().filterIsInstance<Alias>().map { it.alias }.toSet()
                    registerParams
                        .filter { it.name !in namesInSub && it.name !in existingAliases }
                        .forEach {
                            if (it.registerOrPair in Cx16VirtualRegisters) {
                                if(it.type.isIntegerOrBool) {
                                    val mappedParamVar = VarDecl.fromParameter(it)
                                    mods += IAstModification.InsertFirst(mappedParamVar, subroutine)
                                } else {
                                    errors.err("using R0-R15 as register param requires integer or boolean type", it.position)
                                }
                            } else
                                errors.err("can only use R0-R15 as register param for normal subroutines", it.position)
                        }
                }
                return mods
            }
        }

        return noModifications
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<IAstModification> {
        val stmtOfExpression = findParentNode<Statement>(functionCallExpr)
            ?: throw FatalAstException("cannot determine statement scope of function call expression at ${functionCallExpr.position}")

        checkStringParam(functionCallExpr as IFunctionCall, stmtOfExpression)
        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        checkStringParam(functionCallStatement as IFunctionCall, functionCallStatement)
        return noModifications
    }

    override fun after(alias: Alias, parent: Node): Iterable<IAstModification> {
        val tgt = alias.target.targetStatement(program)
        if(tgt is Block) {
            errors.err("cannot alias blocks", alias.target.position)
        }
        return noModifications
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // make all subidentifiers (names of structs, basically) fully scoped
        val subident = typecast.type.subIdentifier
        if(subident!=null && subident.size<2) {
            val struct = typecast.definingScope.lookup(subident) as? StructDecl
            if(struct!=null) {
                typecast.type = DataType.pointer(struct.scopedName)
            }
        }

        return noModifications
    }

    private fun checkStringParam(call: IFunctionCall, stmt: Statement) {
        val targetStatement = call.target.checkFunctionOrLabelExists(program, stmt, errors)
        if(targetStatement!=null) {
            if(targetStatement is Subroutine) {
                for(arg in call.args.zip(targetStatement.parameters)) {
                    if(arg.first.inferType(program).isBytes && arg.second.type.isString) {
                        if((arg.first as? NumericLiteral)?.number!=0.0)
                            errors.err("cannot use byte value for string parameter", arg.first.position)
                    }
                }
            }
        }
    }
}
