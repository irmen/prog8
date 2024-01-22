package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.base.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.findParentNode
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
            } catch (x: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        if(range.to !is NumericLiteral) {
            try {
                val constval = range.to.constValue(program)
                if(constval!=null)
                    modifications += IAstModification.ReplaceNode(range.to, constval, range)
            } catch (x: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        if(range.step !is NumericLiteral) {
            try {
                val constval = range.step.constValue(program)
                if(constval!=null)
                    modifications += IAstModification.ReplaceNode(range.step, constval, range)
            } catch (x: SyntaxError) {
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
                if(decl.type != VarDeclType.VAR) {
                    movements.add(IAstModification.InsertFirst(decl, parentscope))
                    replacements.add(IAstModification.Remove(decl, scope))
                } else {
                    val declToInsert: VarDecl
                    if(decl.names.size>1) {
                        // we need to handle multi-decl here too, the desugarer maybe has not processed it here yet...
                        if(decl.value!=null) {
                            decl.names.forEach { name ->
                                val target = AssignTarget(IdentifierReference(listOf(name), decl.position), null, null, decl.position)
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
                        if(decl.value!=null && (decl.datatype in NumericDatatypes || decl.datatype==DataType.BOOL)) {
                            val target = AssignTarget(IdentifierReference(listOf(decl.name), decl.position), null, null, decl.position)
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
            // check if it's a proper initializer assignment for the variable
            if(decl.value==null && nextAssignment.target.identifier?.targetVarDecl(program)===decl) {
                if(!nextAssignment.value.referencesIdentifier(nextAssignment.target.identifier!!.nameInSource))
                    nextAssignment.origin = AssignmentOrigin.VARINIT
            }
        }

        if(options.splitWordArrays && (decl.datatype==DataType.ARRAY_W || decl.datatype==DataType.ARRAY_UW)) {
            if(!decl.definingBlock.isInLibrary)
                return makeSplitArray(decl)
        }

        if(decl.datatype==DataType.ARRAY_W || decl.datatype==DataType.ARRAY_UW) {
            if ("splitarrays" in decl.definingBlock.options())
                return makeSplitArray(decl)
            if ("splitarrays" in decl.definingModule.options())
                return makeSplitArray(decl)
        }

        return noModifications
    }

    private fun makeSplitArray(decl: VarDecl): Iterable<IAstModification> {
        val newDecl = VarDecl(
            decl.type, decl.origin, decl.datatype, decl.zeropage, decl.arraysize, decl.name, emptyList(),
            decl.value, decl.sharedWithAsm, true, decl.position
        )
        return listOf(IAstModification.ReplaceNode(decl, newDecl, decl.parent))
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        // For non-kernal subroutines and non-asm parameters:
        // inject subroutine params as local variables (if they're not there yet).
        val symbolsInSub = subroutine.allDefinedSymbols
        val namesInSub = symbolsInSub.map{ it.first }.toSet()
        if(subroutine.asmAddress==null) {
            if(!subroutine.isAsmSubroutine && subroutine.parameters.isNotEmpty()) {
                val vars = subroutine.statements.asSequence().filterIsInstance<VarDecl>().map { it.name }.toSet()
                if(!vars.containsAll(subroutine.parameters.map{it.name})) {
                    return subroutine.parameters
                        .filter { it.name !in namesInSub }
                        .map {
                            val vardecl = VarDecl.fromParameter(it)
                            IAstModification.InsertFirst(vardecl, subroutine)
                        }
                }
            }
        }

        return noModifications
    }

    override fun after(bfc: BuiltinFunctionCall, parent: Node): Iterable<IAstModification> {
        val stmtOfExpression = findParentNode<Statement>(bfc)
            ?: throw FatalAstException("cannot determine statement scope of function call expression at ${bfc.position}")
        checkStringParam(bfc as IFunctionCall, stmtOfExpression)
        return noModifications
    }

    override fun after(bfcs: BuiltinFunctionCallStatement, parent: Node): Iterable<IAstModification> {
        checkStringParam(bfcs as IFunctionCall, bfcs)
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

    private fun checkStringParam(call: IFunctionCall, stmt: Statement) {
        val targetStatement = call.target.checkFunctionOrLabelExists(program, stmt, errors)
        if(targetStatement!=null) {
            if(targetStatement is Subroutine) {
                for(arg in call.args.zip(targetStatement.parameters)) {
                    if(arg.first.inferType(program).isBytes && arg.second.type==DataType.STR) {
                        errors.err("cannot use byte value for string parameter", arg.first.position)
                    }
                }
            }
        }
    }
}
