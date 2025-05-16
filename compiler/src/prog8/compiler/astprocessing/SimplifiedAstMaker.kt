package prog8.compiler.astprocessing

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import prog8.ast.FatalAstException
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.sanitize
import prog8.code.source.ImportFileSystem
import prog8.code.source.SourceCode
import prog8.compiler.builtinFunctionReturnType
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile


/**
 *  Convert 'old' compiler-AST into the 'new' simplified AST with baked types.
 */
class SimplifiedAstMaker(private val program: Program, private val target: ICompilationTarget, private val errors: IErrorReporter) {
    fun transform(): PtProgram {
        val ptProgram = PtProgram(
            program.name,
            program.memsizer,
            program.encoding
        )

        // note: modules are not represented any longer in this Ast. All blocks have been moved into the top scope.

        // (re)sort the blocks: "main" first, then all blocks without addresses, then all blocks with addresses (sorted by ascending address)
        val sortedBlocks = program.allBlocks.sortedBy {
            if(it.name=="main") UInt.MIN_VALUE
            else if(it.address==null) UInt.MIN_VALUE+1u
            else it.address
        }
        for (block in sortedBlocks)
            ptProgram.add(transform(block))

        return ptProgram
    }

    private fun transformStatement(statement: Statement): PtNode {
        return when (statement) {
            is AnonymousScope -> throw FatalAstException("AnonymousScopes should have been flattened")
            is Defer -> transform(statement)
            is ChainedAssignment -> throw FatalAstException("ChainedAssignment should have been flattened")
            is Assignment -> transform(statement)
            is Block -> transform(statement)
            is Alias -> throw FatalAstException("alias should have been desugared")
            is Break -> throw FatalAstException("break should have been replaced by Goto")
            is Continue -> throw FatalAstException("continue should have been replaced by Goto")
            is BuiltinFunctionPlaceholder -> throw FatalAstException("BuiltinFunctionPlaceholder should not occur in Ast here")
            is ConditionalBranch -> transform(statement)
            is Directive -> transform(statement)
            is ForLoop -> transform(statement)
            is FunctionCallStatement -> transform(statement)
            is IfElse -> transform(statement)
            is InlineAssembly -> transform(statement)
            is Jump -> transform(statement)
            is Label -> transform(statement)
            is RepeatLoop -> transform(statement)
            is UnrollLoop -> transform(statement)
            is Return -> transform(statement)
            is Subroutine -> {
                if(statement.isAsmSubroutine)
                    transformAsmSub(statement)
                else
                    transformSub(statement)
            }
            is UntilLoop -> throw FatalAstException("until loops must have been converted to jumps")
            is VarDecl -> transform(statement)
            is StructDecl -> transform(statement)
            is When -> transform(statement)
            is WhileLoop -> throw FatalAstException("while loops must have been converted to jumps")
            is OnGoto -> throw FatalAstException("ongoto must have been converted to array and separate call/goto")
            is StructFieldRef -> throw FatalAstException("should not occur as part of the actual AST")
        }
    }

    private fun transformExpression(expr: Expression): PtExpression {
        return when(expr) {
            is AddressOf -> transform(expr)
            is ArrayIndexedExpression -> transform(expr)
            is ArrayLiteral -> transform(expr)
            is BinaryExpression -> transform(expr)
            is CharLiteral -> throw FatalAstException("char literals should have been converted into bytes")
            is ContainmentCheck -> transform(expr)
            is DirectMemoryRead -> transform(expr)
            is FunctionCallExpression -> transform(expr)
            is IdentifierReference -> transform(expr)
            is NumericLiteral -> transform(expr)
            is PrefixExpression -> transform(expr)
            is RangeExpression -> transform(expr)
            is StringLiteral -> transform(expr)
            is TypecastExpression -> transform(expr)
            is IfExpression -> transform(expr)
            is PtrDereference -> transform(expr)
            is PtrIndexedDereference -> transform(expr)
        }
    }

    private fun transform(idxderef: PtrIndexedDereference): PtPointerIndexedDeref {
        val type = idxderef.inferType(program).getOrElse {
            throw FatalAstException("unknown dt")
        }
        val deref = PtPointerIndexedDeref(type, idxderef.position)
        deref.add(transform(idxderef.indexed))
        return deref
    }

    private fun transform(deref: PtrDereference): PtPointerDeref {
        val type = deref.inferType(program).getOrElse {
            throw FatalAstException("unknown dt")
        }
        val start = transform(deref.identifier)
        val deref = PtPointerDeref(type, deref.chain, deref.field,deref.position)
        deref.add(start)
        return deref
    }

    private fun transform(ifExpr: IfExpression): PtIfExpression {
        val type = ifExpr.inferType(program).getOrElse { throw FatalAstException("unknown dt") }
        val ifexpr = PtIfExpression(type, ifExpr.position)
        ifexpr.add(transformExpression(ifExpr.condition))
        ifexpr.add(transformExpression(ifExpr.truevalue))
        ifexpr.add(transformExpression(ifExpr.falsevalue))
        return ifexpr
    }

    private fun transform(srcDefer: Defer): PtDefer {
        val defer = PtDefer(srcDefer.position)
        srcDefer.scope.statements.forEach {
            defer.add(transformStatement(it))
        }
        return defer
    }

    private fun transform(srcAssign: Assignment): PtNode {
        if(srcAssign.isAugmentable) {
            require(srcAssign.target.multi==null)
            val srcExpr = srcAssign.value
            val (operator: String, augmentedValue: Expression?) = when(srcExpr) {
                is BinaryExpression -> {
                    if(srcExpr.left isSameAs srcAssign.target) {
                        val oper = if(srcExpr.operator in ComparisonOperators) srcExpr.operator else srcExpr.operator+'='
                        Pair(oper, srcExpr.right)
                    } else if(srcExpr.right isSameAs srcAssign.target) {
                        val oper = if(srcExpr.operator in ComparisonOperators) srcExpr.operator else srcExpr.operator+'='
                        Pair(oper, srcExpr.left)
                    } else {
                        // either left or right is same as target, other combinations are not supported here
                        Pair("", null)
                    }
                }
                is PrefixExpression -> {
                    require(srcExpr.expression isSameAs srcAssign.target)
                    Pair(srcExpr.operator, srcExpr.expression)
                }
                is TypecastExpression -> {
                    // At this time, there are no special optimized instructions to do an in-place type conversion.
                    // so we simply revert to a regular type converting assignment.
                    // Also, an in-place type cast is very uncommon so probably not worth optimizing anyway.
                    Pair("", null)
                    // the following is what *could* be used here if such instructions *were* available:
//                    if(srcExpr.expression isSameAs srcAssign.target)
//                        Pair("cast", srcExpr.expression)
//                    else {
//                        val subTypeCast = srcExpr.expression as? TypecastExpression
//                        val targetDt = srcAssign.target.inferType(program).getOrElse { DataType.UNDEFINED }
//                        if (subTypeCast!=null && srcExpr.type==targetDt && subTypeCast.expression isSameAs srcAssign.target) {
//                            Pair("cast", subTypeCast)
//                        } else
//                            Pair("", null)
//                    }
                }
                else -> Pair("", null)
            }
            if(augmentedValue!=null) {

                if(srcAssign.target.inferType(program).isPointer) {
                    val expr = srcExpr as BinaryExpression
                    if(expr.operator=="+") {
                        // pointer arithmetic: add the size of the struct times the argument
                        val leftDt = expr.left.inferType(program).getOrUndef()
                        require(leftDt.isPointer && !expr.right.inferType(program).isPointer)
                        val structSize = leftDt.size(program.memsizer)
                        val constValue = augmentedValue.constValue(program)
                        if(constValue!=null) {
                            val total = constValue.number*structSize
                            if (total == 0.0)
                                return PtNop(srcAssign.position)
                            else {
                                val assign = PtAugmentedAssign(operator, srcAssign.position)
                                assign.add(transform(srcAssign.target))
                                assign.add(PtNumber(BaseDataType.UWORD, total, srcAssign.position))
                                return assign
                            }
                        } else {
                            val multiplication = PtBinaryExpression("*", DataType.UWORD, srcAssign.position)
                            multiplication.add(transformExpression(augmentedValue))
                            multiplication.add(PtNumber(BaseDataType.UWORD, structSize.toDouble(), srcAssign.position))
                            val assign = PtAugmentedAssign(operator, srcAssign.position)
                            assign.add(transform(srcAssign.target))
                            assign.add(multiplication)
                            return assign
                        }
                    } else
                        throw FatalAstException("unexpected augmented assignment operator on pointer ${expr.operator}")
                }

                val assign = PtAugmentedAssign(operator, srcAssign.position)
                assign.add(transform(srcAssign.target))
                assign.add(transformExpression(augmentedValue))
                return assign
            }
        }

        if(srcAssign.origin == AssignmentOrigin.VARINIT && srcAssign.parent is Block && srcAssign.value.constValue(program)?.number==0.0)
            throw FatalAstException("should not have a redundant block-level variable=0 assignment; it will be zeroed as part of BSS clear")

        val assign = PtAssignment(srcAssign.position, srcAssign.origin==AssignmentOrigin.VARINIT)
        val multi = srcAssign.target.multi
        if(multi==null) {
            assign.add(transform(srcAssign.target))
        } else {
            multi.forEach { target -> assign.add(transform(target)) }
        }
        assign.add(transformExpression(srcAssign.value))
        return assign
    }

    private fun transform(srcTarget: AssignTarget): PtAssignTarget {
        val target = PtAssignTarget(srcTarget.void, srcTarget.position)
        when {
            srcTarget.identifier!=null -> target.add(transform(srcTarget.identifier!!))
            srcTarget.arrayindexed!=null -> target.add(transform(srcTarget.arrayindexed!!))
            srcTarget.memoryAddress!=null -> target.add(transform(srcTarget.memoryAddress!!))
            srcTarget.pointerDereference !=null -> {
                val deref = transform(srcTarget.pointerDereference!!)
                target.add(deref)
            }
            !srcTarget.void -> throw FatalAstException("invalid AssignTarget")
        }
        return target
    }

    private fun transform(identifier: IdentifierReference): PtExpression {
        val (target, type) = identifier.targetNameAndType(program)
        return PtIdentifier(target, type, identifier.position)
    }

    private fun transform(srcBlock: Block): PtBlock {
        var forceOutput = false
        var veraFxMuls = false
        var noSymbolPrefixing = false
        var ignoreUnused = false
        val directives = srcBlock.statements.filterIsInstance<Directive>()
        for (directive in directives.filter { it.directive == "%option" }) {
            for (arg in directive.args) {
                when (arg.string) {
                    "no_symbol_prefixing" -> noSymbolPrefixing = true
                    "ignore_unused" -> ignoreUnused = true
                    "force_output" -> forceOutput = true
                    "merge" -> { /* ignore this one */ }
                    "verafxmuls" -> veraFxMuls = true
                    else -> throw FatalAstException("weird directive option: ${arg.string}")
                }
            }
        }


        val (vardecls, statements) = srcBlock.statements.partition { it is VarDecl }
        val src = srcBlock.definingModule.source
        val block = PtBlock(srcBlock.name, srcBlock.isInLibrary, src,
            PtBlock.Options(srcBlock.address, forceOutput, noSymbolPrefixing, veraFxMuls, ignoreUnused),
            srcBlock.position)

        for(directive in directives.filter { it.directive == "%jmptable" }) {
            val table = PtJmpTable(directive.position)
            directive.args.forEach {
                table.add(PtIdentifier(it.string!!, DataType.UNDEFINED, it.position))
            }
            block.add(table)
        }

        makeScopeVarsDecls(vardecls).forEach { block.add(it) }
        for (stmt in statements)
            block.add(transformStatement(stmt))
        return block
    }

    private fun makeScopeVarsDecls(vardecls: Iterable<Statement>): Iterable<PtNamedNode> {
        val decls = mutableListOf<PtNamedNode>()
        vardecls.forEach {
            decls.add(transformStatement(it as VarDecl) as PtNamedNode)
        }
        return decls
    }

    private fun transform(srcBranch: ConditionalBranch): PtConditionalBranch {
        val branch = PtConditionalBranch(srcBranch.condition, srcBranch.position)
        val trueScope = PtNodeGroup()
        val falseScope = PtNodeGroup()
        for (stmt in srcBranch.truepart.statements)
            trueScope.add(transformStatement(stmt))
        for (stmt in srcBranch.elsepart.statements)
            falseScope.add(transformStatement(stmt))
        branch.add(trueScope)
        branch.add(falseScope)
        return branch
    }

    private fun transform(directive: Directive): PtNode {
        return when(directive.directive) {
            "%breakpoint" -> PtBreakpoint(directive.position)
            "%align" -> {
                val align = directive.args[0].int!!
                if(align<2u || align>65536u)
                    errors.err("invalid alignment size", directive.position)
                PtAlign(align, directive.position)
            }
            "%asmbinary" -> {
                val filename = directive.args[0].string!!
                val offset: UInt? = if(directive.args.size>=2) directive.args[1].int!! else null
                val length: UInt? = if(directive.args.size>=3) directive.args[2].int!! else null
                val abspath = if(File(filename).isFile) {
                    Path(filename).sanitize()
                } else {
                    val sourcePath = Path(directive.definingModule.source.origin)
                    sourcePath.resolveSibling(filename).sanitize()
                }
                if(abspath.toFile().isFile)
                    PtIncludeBinary(abspath, offset, length, directive.position)
                else
                    throw FatalAstException("included file doesn't exist")
            }
            "%asminclude" -> {
                val result = loadAsmIncludeFile(directive.args[0].string!!, directive.definingModule.source)
                val assembly = result.getOrElse { throw it }
                PtInlineAssembly(assembly.trimEnd().trimStart('\r', '\n'), false, directive.position)
            }
            else -> {
                // other directives don't output any code (but could end up in option flags somewhere else)
                PtNop(directive.position)
            }
        }
    }

    private fun transform(srcFor: ForLoop): PtForLoop {
        val forloop = PtForLoop(srcFor.position)
        forloop.add(transform(srcFor.loopVar))
        forloop.add(transformExpression(srcFor.iterable))
        val statements = PtNodeGroup()
        for (stmt in srcFor.body.statements)
            statements.add(transformStatement(stmt))
        forloop.add(statements)
        return forloop
    }

    private fun transform(srcCall: FunctionCallStatement): PtExpression {
        val singleName = srcCall.target.nameInSource.singleOrNull()
        if(singleName!=null && singleName in BuiltinFunctions) {
            // it is a builtin function. Create a special Ast node for that.
            val type = builtinFunctionReturnType(singleName).getOrUndef()
            val noSideFx = BuiltinFunctions.getValue(singleName).pure
            val call = PtBuiltinFunctionCall(singleName, true, noSideFx, type, srcCall.position)
            for (arg in srcCall.args)
                call.add(transformExpression(arg))
            return call
        }

        // regular function call
        val (target, type) = srcCall.target.targetNameAndType(program)
        val call = PtFunctionCall(target, true, type, srcCall.position)
        for (arg in srcCall.args)
            call.add(transformExpression(arg))
        return call
    }

    private fun transform(srcCall: FunctionCallExpression): PtExpression {
        val singleName = srcCall.target.nameInSource.singleOrNull()
        if(singleName!=null) {
            val builtinFunc = BuiltinFunctions[singleName]
            if (builtinFunc != null) {
                // it's a builtin function. Create special node type for this.
                val noSideFx = builtinFunc.pure
                val type = srcCall.inferType(program).getOrElse { throw FatalAstException("unknown dt") }
                val call = PtBuiltinFunctionCall(singleName, false, noSideFx, type, srcCall.position)
                for (arg in srcCall.args)
                    call.add(transformExpression(arg))
                return call
            }
        }

        val targetStruct = srcCall.target.targetStructDecl()
        val call =
            if(targetStruct!=null) {
                // a call to a struct yields a pointer to a struct instance and means: allocate a statically initialized struct instance of that type
                val pointertype = DataType.pointerToType(targetStruct)
                PtBuiltinFunctionCall("structalloc", false, true, pointertype, srcCall.position)
            } else {
                // regular function call
                val (target, _) = srcCall.target.targetNameAndType(program)
                val iType = srcCall.inferType(program)
                PtFunctionCall(target, iType.isUnknown && srcCall.parent !is Assignment, iType.getOrElse { DataType.UNDEFINED }, srcCall.position)
            }

        for (arg in srcCall.args)
            call.add(transformExpression(arg))
        return call
    }

    private fun transform(srcIf: IfElse): PtNode {

        fun codeForStatusflag(fcall: FunctionCallExpression, flag: Statusflag, equalToZero: Boolean): PtNodeGroup {
            // if the condition is a call to something that returns a boolean in a status register (C, Z, V, N),
            // a smarter branch is possible using a conditional branch node.
            val (branchTrue, branchFalse) = if(equalToZero) {
                when (flag) {
                    Statusflag.Pc -> BranchCondition.CC to BranchCondition.CS
                    Statusflag.Pz -> BranchCondition.NZ to BranchCondition.Z
                    Statusflag.Pv -> BranchCondition.VC to BranchCondition.VS
                    Statusflag.Pn -> BranchCondition.POS to BranchCondition.NEG
                }
            } else {
                when (flag) {
                    Statusflag.Pc -> BranchCondition.CS to BranchCondition.CC
                    Statusflag.Pz -> BranchCondition.Z to BranchCondition.NZ
                    Statusflag.Pv -> BranchCondition.VS to BranchCondition.VC
                    Statusflag.Pn -> BranchCondition.NEG to BranchCondition.POS
                }
            }
            val jump = srcIf.truepart.statements.firstOrNull() as? Jump
            if (jump!=null) {
                // only a jump, use a conditional branch to the jump target.
                val nodes = PtNodeGroup()
                nodes.add(transformExpression(fcall))
                val branch = PtConditionalBranch(branchTrue, srcIf.position)
                val ifScope = PtNodeGroup()
                ifScope.add(transform(jump))
                val elseScope = PtNodeGroup()
                if(srcIf.elsepart.isNotEmpty())
                    throw FatalAstException("if-else with only a goto should no longer have statements in the else part")
                branch.add(ifScope)
                branch.add(elseScope)
                nodes.add(branch)
                return nodes
            } else {
                // skip over the true part if the condition is false
                val nodes = PtNodeGroup()
                nodes.add(transformExpression(fcall))
                val branch = PtConditionalBranch(branchFalse, srcIf.position)
                val ifScope = PtNodeGroup()
                val elseLabel = program.makeLabel("celse")
                val endLabel = program.makeLabel("cend")
                val scopedElseLabel = (srcIf.definingScope.scopedName + elseLabel).joinToString(".")
                val scopedEndLabel = (srcIf.definingScope.scopedName + endLabel).joinToString(".")
                val elseLbl = PtIdentifier(scopedElseLabel, DataType.UNDEFINED, srcIf.position)
                val endLbl = PtIdentifier(scopedEndLabel, DataType.UNDEFINED, srcIf.position)
                ifScope.add(PtJump(srcIf.position).also { it.add(elseLbl) })
                val elseScope = PtNodeGroup()
                branch.add(ifScope)
                branch.add(elseScope)
                nodes.add(branch)
                for (stmt in srcIf.truepart.statements)
                    nodes.add(transformStatement(stmt))
                if(srcIf.elsepart.isNotEmpty())
                    nodes.add(PtJump(srcIf.position).also { it.add(endLbl) })
                nodes.add(PtLabel(elseLabel, srcIf.position))
                if(srcIf.elsepart.isNotEmpty()) {
                    for (stmt in srcIf.elsepart.statements)
                        nodes.add(transformStatement(stmt))
                }
                if(srcIf.elsepart.isNotEmpty())
                    nodes.add(PtLabel(endLabel, srcIf.position))
                return nodes
            }
        }

        // if something_returning_Pc()   ->  if_cc
        val binexpr = srcIf.condition as? BinaryExpression
        if(binexpr!=null && binexpr.right.constValue(program)?.number==0.0) {
            if(binexpr.operator=="==" || binexpr.operator=="!=") {
                val fcall = binexpr.left as? FunctionCallExpression
                if(fcall!=null) {
                    val returnRegs = fcall.target.targetSubroutine()?.asmReturnvaluesRegisters
                    if(returnRegs!=null && returnRegs.size==1 && returnRegs[0].statusflag!=null) {
                        return codeForStatusflag(fcall, returnRegs[0].statusflag!!, binexpr.operator == "==")
                    }
                }
            }
        } else {
            val fcall = srcIf.condition as? FunctionCallExpression
            if (fcall != null) {
                val returnRegs = fcall.target.targetSubroutine()?.asmReturnvaluesRegisters
                if(returnRegs!=null && returnRegs.size==1 && returnRegs[0].statusflag!=null) {
                    return codeForStatusflag(fcall, returnRegs[0].statusflag!!, false)
                }
            }

            val prefix = srcIf.condition as? PrefixExpression
            if(prefix!=null && prefix.operator=="not") {
                val prefixedFcall = prefix.expression as? FunctionCallExpression
                if (prefixedFcall != null) {
                    val returnRegs = prefixedFcall.target.targetSubroutine()?.asmReturnvaluesRegisters
                    if (returnRegs != null && returnRegs.size == 1 && returnRegs[0].statusflag != null) {
                        return codeForStatusflag(prefixedFcall, returnRegs[0].statusflag!!, true)
                    }
                }
            }
        }

        val ifelse = PtIfElse(srcIf.position)
        ifelse.add(transformExpression(srcIf.condition))
        val ifScope = PtNodeGroup()
        val elseScope = PtNodeGroup()
        for (stmt in srcIf.truepart.statements)
            ifScope.add(transformStatement(stmt))
        for (stmt in srcIf.elsepart.statements)
            elseScope.add(transformStatement(stmt))
        ifelse.add(ifScope)
        ifelse.add(elseScope)
        return ifelse
    }

    private fun transform(srcNode: InlineAssembly): PtInlineAssembly {
        val assembly = srcNode.assembly.trimEnd().trimStart('\r', '\n')
        return PtInlineAssembly(assembly, srcNode.isIR, srcNode.position)
    }

    private fun transform(srcJump: Jump): PtJump {
        val target = transformExpression(srcJump.target)
        return PtJump(srcJump.position).also { it.add(target) }
    }

    private fun transform(label: Label): PtLabel =
        PtLabel(label.name, label.position)

    private fun transform(srcRepeat: RepeatLoop): PtRepeatLoop {
        if(srcRepeat.iterations==null)
            throw FatalAstException("repeat-forever loop should have been replaced with label+jump")
        val repeat = PtRepeatLoop(srcRepeat.position)
        repeat.add(transformExpression(srcRepeat.iterations!!))
        val scope = PtNodeGroup()
        for (statement in srcRepeat.body.statements) {
            scope.add(transformStatement(statement))
        }
        repeat.add(scope)
        return repeat
    }

    private fun transform(srcUnroll: UnrollLoop): PtNodeGroup {
        val result = PtNodeGroup()
        repeat(srcUnroll.iterations.constValue(program)!!.number.toInt()) {
            srcUnroll.body.statements.forEach {
                result.add(transformStatement(it))
            }
        }
        return result
    }

    private fun transform(srcNode: Return): PtReturn {
        val ret = PtReturn(srcNode.position)
        srcNode.values.forEach { ret.add(transformExpression(it)) }
        return ret
    }

    private fun transformAsmSub(srcSub: Subroutine): PtAsmSub {
        val params = srcSub.asmParameterRegisters.zip(srcSub.parameters.map { PtSubroutineParameter(it.name, it.type, it.registerOrPair, it.position) })
        val varbank = if(srcSub.asmAddress?.varbank==null) null else transform(srcSub.asmAddress!!.varbank!!)
        if(varbank!=null && varbank !is PtIdentifier)
            throw FatalAstException("varbank must be a regular variable")
        val asmAddr = if(srcSub.asmAddress==null) null else {
            val constAddr = srcSub.asmAddress!!.address.constValue(program)
            if(constAddr==null) throw FatalAstException("extsub address should be a constant")
            PtAsmSub.Address(srcSub.asmAddress!!.constbank, varbank, constAddr.number.toUInt())
        }
        val sub = PtAsmSub(srcSub.name,
            asmAddr,
            srcSub.asmClobbers,
            params,
            srcSub.asmReturnvaluesRegisters.zip(srcSub.returntypes),
            srcSub.inline,
            srcSub.position)
        sub.parameters.forEach { it.second.parent=sub }
        if(varbank!=null)
            asmAddr?.varbank?.parent = sub

        if(srcSub.asmAddress==null) {
            var combinedTrueAsm = ""
            var combinedIrAsm = ""
            for (asm in srcSub.statements) {
                asm as InlineAssembly
                if(asm.isIR)
                    combinedIrAsm += asm.assembly.trimEnd() + "\n"
                else
                    combinedTrueAsm += asm.assembly.trimEnd() + "\n"
            }

            if(combinedTrueAsm.isNotEmpty()) {
                combinedTrueAsm = combinedTrueAsm.trimEnd().trimStart('\r', '\n')
                sub.add(PtInlineAssembly(combinedTrueAsm, false, srcSub.statements[0].position))
            }
            if(combinedIrAsm.isNotEmpty()) {
                combinedIrAsm = combinedIrAsm.trimEnd().trimStart('\r', '\n')
                sub.add(PtInlineAssembly(combinedIrAsm, true, srcSub.statements[0].position))
            }
            if(combinedIrAsm.isEmpty() && combinedTrueAsm.isEmpty())
                sub.add(PtInlineAssembly("", true, srcSub.position))
        }

        return sub
    }

    private fun transformSub(srcSub: Subroutine): PtSub {
        val (vardecls, statements) = srcSub.statements.partition { it is VarDecl }
        // if a sub returns 'str', replace with uword.  Simplified AST and I.R. don't contain 'str' datatype anymore.
        val returnTypes = srcSub.returntypes.map {
            if(it.isString) DataType.UWORD else it
        }
        // do not bother about the 'inline' hint of the source subroutine.
        val sub = PtSub(srcSub.name,srcSub.position)
        val signature = PtSubSignature( returnTypes, srcSub.position)
        srcSub.parameters.forEach { signature.add(PtSubroutineParameter(it.name, it.type, it.registerOrPair, it.position)) }
        sub.add(signature)
        makeScopeVarsDecls(vardecls).forEach { sub.add(it) }
        for (statement in statements)
            sub.add(transformStatement(statement))
        return sub
    }

    private fun transform(srcVar: VarDecl): PtNode {
        when(srcVar.type) {
            VarDeclType.VAR -> {
                val value = if(srcVar.value!=null) transformExpression(srcVar.value!!) else null
                if(srcVar.dirty && value!=null)
                    throw FatalAstException("dirty with initializer value $srcVar")
//                if(value==null && !srcVar.dirty)
//                    throw FatalAstException("no init value but not marked dirty $srcVar")
                return PtVariable(
                    srcVar.name,
                    srcVar.datatype,
                    srcVar.zeropage,
                    srcVar.alignment,
                    srcVar.dirty,
                    value,
                    srcVar.arraysize?.constIndex()?.toUInt(),
                    srcVar.position
                )
            }
            VarDeclType.CONST -> return PtConstant(srcVar.name, srcVar.datatype, (srcVar.value as NumericLiteral).number, srcVar.position)
            VarDeclType.MEMORY -> return PtMemMapped(srcVar.name, srcVar.datatype, (srcVar.value as NumericLiteral).number.toUInt(), srcVar.arraysize?.constIndex()?.toUInt(), srcVar.position)
        }
    }

    private fun transform(struct: StructDecl): PtStructDecl {
        return PtStructDecl(struct.name, struct.fields, struct.position)
    }

    private fun transform(srcWhen: When): PtWhen {
        val w = PtWhen(srcWhen.position)
        w.add(transformExpression(srcWhen.condition))
        val choices = PtNodeGroup()
        for (choice in srcWhen.choices)
            choices.add(transform(choice))
        w.add(choices)
        return w
    }

    private fun transform(srcChoice: WhenChoice): PtWhenChoice {
        val choice = PtWhenChoice(srcChoice.values==null, srcChoice.position)
        val values = PtNodeGroup()
        val statements = PtNodeGroup()
        if(!choice.isElse) {
            for (value in srcChoice.values!!)
                values.add(transformExpression(value))
        }
        for (stmt in srcChoice.statements.statements)
            statements.add(transformStatement(stmt))
        choice.add(values)
        choice.add(statements)
        return choice
    }

    private fun transform(src: AddressOf): PtAddressOf {
        val addr = PtAddressOf(src.inferType(program).getOrUndef(), src.position, src.msb)
        if(src.identifier!=null)
            addr.add(transform(src.identifier!!))
        if (src.arrayIndex != null)
            addr.add(transformExpression(src.arrayIndex!!.indexExpr))
        if (src.dereference!=null)
            addr.add(transformExpression(src.dereference!!))
        return addr
    }

    private fun transform(srcArr: ArrayIndexedExpression): PtArrayIndexer {
        val dt = srcArr.arrayvar.targetVarDecl()!!.datatype
        if(!dt.isArray && !dt.isString && !dt.isPointer)
            throw FatalAstException("array indexing can only be used on array, string or pointer variables ${srcArr.position}")
        val eltType = srcArr.inferType(program).getOrElse { throw FatalAstException("unknown dt") }
        val array = PtArrayIndexer(eltType, srcArr.position)
        array.add(transform(srcArr.arrayvar))
        array.add(transformExpression(srcArr.indexer.indexExpr))
        return array
    }

    private fun transform(srcArr: ArrayLiteral): PtArray {
        val arr = PtArray(srcArr.inferType(program).getOrElse { throw FatalAstException("array must know its type") }, srcArr.position)
        for (elt in srcArr.value) {
            val child = transformExpression(elt)
            require(child is PtAddressOf || child is PtBool || child is PtNumber) { "array element invalid type $child" }
            arr.add(child)
        }
        return arr
    }

    private fun transform(srcExpr: BinaryExpression): PtExpression {
        val type = srcExpr.inferType(program).getOrElse {
            throw FatalAstException("unknown dt $srcExpr")
        }
        if(srcExpr.operator==".") {
            when (srcExpr.right) {
                is IdentifierReference -> {
                    val chain = srcExpr.right as IdentifierReference
                    val deref = PtPointerDeref(type, chain.nameInSource, null, srcExpr.position)
                    deref.add(transformExpression(srcExpr.left))
                    return deref
                }

                is ArrayIndexedExpression -> {
                    errors.err("at the moment it is not possible to chain array syntax on pointers like  ...p1[x].p2[y]... use separate expressions for the time being", srcExpr.right.position)  // TODO add support for chained array syntax on pointers (rewrite ast?)
                    return PtIdentifier("<dummy>", DataType.UNDEFINED, Position.DUMMY)
                }

                else -> throw FatalAstException("unknown deref at ${srcExpr.position}")
            }
        } else {
            if(srcExpr.left.inferType(program).isPointer || srcExpr.right.inferType(program).isPointer) {
                return when (srcExpr.operator) {
                    "+", "-" -> transformWithPointerArithmetic(srcExpr)
                    in ComparisonOperators -> transformWithPointerComparison(srcExpr)
                    else -> throw FatalAstException("unsupported operator on pointer: ${srcExpr.operator} at ${srcExpr.position}")
                }
            } else {
                val expr = PtBinaryExpression(srcExpr.operator, type, srcExpr.position)
                expr.add(transformExpression(srcExpr.left))
                expr.add(transformExpression(srcExpr.right))
                return expr
            }
        }
    }

    private fun transformWithPointerComparison(expr: BinaryExpression): PtBinaryExpression {
        val leftDt = expr.left.inferType(program)
        val rightDt = expr.right.inferType(program)
        require(leftDt.isPointer || leftDt.isWords)
        require(rightDt.isPointer || rightDt.isWords)
        val comparison = PtBinaryExpression(expr.operator, DataType.BOOL, expr.position)
        comparison.add(transformExpression(expr.left))
        comparison.add(transformExpression(expr.right))
        return comparison
    }

    private fun transformWithPointerArithmetic(expr: BinaryExpression): PtExpression {
        val operator = expr.operator
        require(operator=="+" || operator=="-")
        // below where '+' is used, you can substitute '-'.
        // pointer arithmetic:  ptr + value
        val leftDt = expr.left.inferType(program).getOrUndef()
        val rightDt = expr.right.inferType(program).getOrUndef()

        if(leftDt.isPointer && !rightDt.isPointer) {
            val resultDt = leftDt
            val structSize = leftDt.size(program.memsizer)
            val constValue = expr.right.constValue(program)
            if(constValue!=null) {
                // ptr + constvalue * structsize
                val total = constValue.number*structSize
                if (total == 0.0)
                    return transformExpression(expr.left)
                else {
                    val plusorminus = PtBinaryExpression(operator, resultDt, expr.position)
                    plusorminus.add(transformExpression(expr.left))
                    plusorminus.add(PtNumber(BaseDataType.UWORD, total, expr.position))
                    return plusorminus
                }
            } else {
                // ptr + right * structSize
                val total = PtBinaryExpression("*", DataType.UWORD, expr.position).also { it.add(transformExpression(expr.right)) }
                total.add(transformExpression(expr.right))
                total.add(PtNumber(BaseDataType.UWORD, structSize.toDouble(), expr.position))
                val plusorminus = PtBinaryExpression(operator, resultDt, expr.position)
                plusorminus.add(transformExpression(expr.left))
                plusorminus.add(total)
                return plusorminus
            }
        } else if(!leftDt.isPointer && rightDt.isPointer) {
            val resultDt = rightDt
            val structSize = rightDt.size(program.memsizer)
            val constValue = expr.left.constValue(program)
            if(constValue!=null) {
                // ptr + constvalue * structsize
                val total = constValue.number*structSize
                if (total == 0.0)
                    return transformExpression(expr.right)
                else {
                    val plusorminus = PtBinaryExpression(operator, resultDt, expr.position)
                    plusorminus.add(transformExpression(expr.right))
                    plusorminus.add(PtNumber(BaseDataType.UWORD, total, expr.position))
                    return plusorminus
                }
            } else {
                // ptr + left  * structSize
                val total = PtBinaryExpression("*", DataType.UWORD, expr.position)
                total.add(transformExpression(expr.left))
                total.add(PtNumber(BaseDataType.UWORD, structSize.toDouble(), expr.position))
                val plusorminus = PtBinaryExpression(operator, resultDt, expr.position)
                plusorminus.add(transformExpression(expr.right))
                plusorminus.add(total)
                return plusorminus
            }
        } else {
            throw FatalAstException("weird pointer arithmetic ${expr.position}")
        }
    }

    private fun transform(srcCheck: ContainmentCheck): PtExpression {

        fun desugar(range: RangeExpression): PtExpression {
            require(range.from.inferType(program)==range.to.inferType(program))
            val expr = PtBinaryExpression("and", DataType.BOOL, srcCheck.position)
            val x1 = transformExpression(srcCheck.element)
            val x2 = transformExpression(srcCheck.element)
            val eltDt = srcCheck.element.inferType(program)
            if(eltDt.isInteger) {
                val low = PtBinaryExpression("<=", DataType.BOOL, srcCheck.position)
                low.add(transformExpression(range.from))
                low.add(x1)
                expr.add(low)
                val high = PtBinaryExpression("<=", DataType.BOOL, srcCheck.position)
                high.add(x2)
                high.add(transformExpression(range.to))
                expr.add(high)
            } else {
                val low = PtBinaryExpression("<=", DataType.BOOL, srcCheck.position)
                val lowFloat = PtTypeCast(DataType.FLOAT, range.from.position)
                lowFloat.add(transformExpression(range.from))
                low.add(lowFloat)
                low.add(x1)
                expr.add(low)
                val high = PtBinaryExpression("<=", DataType.BOOL, srcCheck.position)
                high.add(x2)
                val highFLoat = PtTypeCast(DataType.FLOAT, range.to.position)
                highFLoat.add(transformExpression(range.to))
                high.add(highFLoat)
                expr.add(high)
            }
            return expr
        }

        when(srcCheck.iterable) {
            is IdentifierReference, is ArrayLiteral -> {
                val check = PtContainmentCheck(srcCheck.position)
                check.add(transformExpression(srcCheck.element))
                check.add(transformExpression(srcCheck.iterable))
                return check
            }
            is RangeExpression -> {
                val range = srcCheck.iterable as RangeExpression
                val constRange = range.toConstantIntegerRange()
                val constElt = srcCheck.element.constValue(program)?.number
                val step = range.step.constValue(program)?.number
                if(constElt!=null && constRange!=null) {
                    return PtBool(constRange.first<=constElt && constElt<=constRange.last, srcCheck.position)
                }
                else if(step==1.0) {
                    // x in low to high --> low <=x and x <= high
                    return desugar(range)
                } else if(step==-1.0) {
                    // x in high downto low -> low <=x and x <= high
                    val tmp = range.to
                    range.to = range.from
                    range.from = tmp
                    return desugar(range)
                } else {
                    errors.err("cannot use step size different than 1 or -1 in a non constant range containment check", srcCheck.position)
                    return PtBool(false, srcCheck.position)
                }
            }
            else -> throw FatalAstException("iterable in containmentcheck must always be an identifier (referencing string or array) or a range expression $srcCheck")
        }
    }

    private fun transform(memory: DirectMemoryWrite): PtMemoryByte {
        val mem = PtMemoryByte(memory.position)
        mem.add(transformExpression(memory.addressExpression))
        return mem
    }

    private fun transform(memory: DirectMemoryRead): PtMemoryByte {
        val mem = PtMemoryByte(memory.position)
        mem.add(transformExpression(memory.addressExpression))
        return mem
    }

    private fun transform(number: NumericLiteral): PtExpression {
        return if(number.type==BaseDataType.BOOL)
            PtBool(number.asBooleanValue, number.position)
        else
            PtNumber(number.type, number.number, number.position)
    }

    private fun transform(srcPrefix: PrefixExpression): PtPrefix {
        val type = srcPrefix.inferType(program).getOrElse { throw FatalAstException("unknown dt") }
        val prefix = PtPrefix(srcPrefix.operator, type, srcPrefix.position)
        prefix.add(transformExpression(srcPrefix.expression))
        return prefix
    }

    private fun transform(srcRange: RangeExpression): PtRange {
        require(srcRange.from.inferType(program)==srcRange.to.inferType(program))
        var type = srcRange.inferType(program).getOrElse { throw FatalAstException("unknown dt") }
        if(type.isSplitWordArray) {
            // ranges are never a split word array!
            when(type.sub) {
                BaseDataType.WORD -> type = DataType.arrayFor(BaseDataType.WORD, false)
                BaseDataType.UWORD -> type = DataType.arrayFor(BaseDataType.UWORD, false)
                else -> { }
            }
        }
        val range=PtRange(type, srcRange.position)
        range.add(transformExpression(srcRange.from))
        range.add(transformExpression(srcRange.to))
        range.add(transformExpression(srcRange.step) as PtNumber)
        return range
    }

    private fun transform(srcString: StringLiteral): PtString =
        PtString(srcString.value, srcString.encoding, srcString.position)

    private fun transform(srcCast: TypecastExpression): PtTypeCast {
        val cast = PtTypeCast(srcCast.type, srcCast.position)
        cast.add(transformExpression(srcCast.expression))
        require(cast.type!=cast.value.type) {
            "bogus typecast shouldn't occur at ${srcCast.position}" }
        return cast
    }

    private fun loadAsmIncludeFile(filename: String, source: SourceCode): Result<String, NoSuchFileException> {
        return if (SourceCode.isLibraryResource(filename)) {
            return com.github.michaelbull.result.runCatching {
                val physFilename = SourceCode.withoutPrefix(filename)
                ImportFileSystem.getResource("/prog8lib/$physFilename").text
            }.mapError { NoSuchFileException(File(filename)) }
        } else {
            val sib = Path(source.origin).resolveSibling(filename)
            if (sib.isRegularFile())
                Ok(ImportFileSystem.getFile(sib, source.isFromLibrary).text)
            else
                Ok(ImportFileSystem.getFile(Path(filename), source.isFromLibrary).text)
        }
    }

}