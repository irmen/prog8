package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*


internal class CodeDesugarer(val program: Program, private val target: ICompilationTarget, private val errors: IErrorReporter) : AstWalker() {

    // Some more code shuffling to simplify the Ast that the codegenerator has to process.
    // Several changes have already been done by the StatementReorderer !
    // But the ones here are simpler and are repeated once again after all optimization steps
    // have been performed (because those could re-introduce nodes that have to be desugared)
    //
    // List of modifications:
    // - replace 'break' and 'continue' statements by a goto + generated after label.
    // - replace while and do-until loops by just jumps.
    // - replace peek() and poke() by direct memory accesses.
    // - repeat-forever loops replaced by label+jump.
    // - pointer[word] replaced by @(pointer+word)
    // - @(&var) and @(&var+1) replaced by lsb(var) and msb(var) if var is a word
    // - flatten chained assignments
    // - remove alias nodes
    // - convert on..goto/call to jumpaddr array and separate goto/call
    // - replace implicit pointer dereference chains (a.b.c.d) with explicit ones (a^^.b^^.c^^.d)
    // - replace ptr^^ by @(ptr) if ptr is just an uword.
    // - replace p1^^ = p2^^  by memcopy.

    override fun after(alias: Alias, parent: Node): Iterable<IAstModification> {
        return listOf(IAstModification.Remove(alias, parent as IStatementContainer))
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(decl.datatype.isPointer && decl.datatype.sub==BaseDataType.STR) {
            errors.info("^^str replaced by ^^ubyte", decl.position)
            val decl2 = decl.copy(DataType.pointer(BaseDataType.UBYTE))
            return listOf(IAstModification.ReplaceNode(decl, decl2, parent))
        }
        return noModifications
    }

    override fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> {
        fun jumpAfter(stmt: Statement): Iterable<IAstModification> {
            val label = program.makeLabel("after", breakStmt.position)
            return listOf(
                IAstModification.ReplaceNode(breakStmt, program.jumpLabel(label), parent),
                IAstModification.InsertAfter(stmt, label, stmt.parent as IStatementContainer)
            )
        }

        var partof = parent
        while(true) {
            when (partof) {
                is Subroutine, is Block, is ParentSentinel -> {
                    errors.err("break in wrong scope", breakStmt.position)
                    return noModifications
                }
                is ForLoop,
                is RepeatLoop,
                is UntilLoop,
                is WhileLoop -> return jumpAfter(partof)
                else -> partof = partof.parent
            }
        }
    }

    override fun before(continueStmt: Continue, parent: Node): Iterable<IAstModification> {
        fun jumpToBottom(scope: IStatementContainer): Iterable<IAstModification> {
            val label = program.makeLabel("cont", continueStmt.position)
            return listOf(
                IAstModification.ReplaceNode(continueStmt, program.jumpLabel(label), parent),
                IAstModification.InsertLast(label, scope)
            )
        }

        fun jumpToBefore(loop: WhileLoop): Iterable<IAstModification> {
            val label = program.makeLabel("cont", continueStmt.position)
            return listOf(
                IAstModification.ReplaceNode(continueStmt, program.jumpLabel(label), parent),
                IAstModification.InsertBefore(loop, label, loop.parent as IStatementContainer)
            )
        }

        var partof = parent
        while(true) {
            when (partof) {
                is Subroutine, is Block, is ParentSentinel -> {
                    errors.err("continue in wrong scope", continueStmt.position)
                    return noModifications
                }
                is ForLoop -> return jumpToBottom(partof.body)
                is RepeatLoop -> return jumpToBottom(partof.body)
                is UntilLoop -> return jumpToBottom(partof.body)
                is WhileLoop -> return jumpToBefore(partof)
                else -> partof = partof.parent
            }
        }
    }

    override fun after(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        /*
do { STUFF } until CONDITION
    ===>
_loop:
  STUFF
if not CONDITION
   goto _loop
         */
        val pos = untilLoop.position
        val loopLabel = program.makeLabel("untilloop", pos)
        val replacement = AnonymousScope(mutableListOf(
            loopLabel,
            untilLoop.body,
            IfElse(invertCondition(untilLoop.condition, program),
                AnonymousScope(mutableListOf(program.jumpLabel(loopLabel)), pos),
                AnonymousScope.empty(),
                pos)
        ), pos)
        return listOf(IAstModification.ReplaceNode(untilLoop, replacement, parent))
    }

    override fun after(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {

        /*
        while true -> repeat
        while false -> discard
         */

        val constCondition = whileLoop.condition.constValue(program)?.asBooleanValue
        if(constCondition==true) {
            errors.warn("condition is always true", whileLoop.condition.position)
            val repeat = RepeatLoop(null, whileLoop.body, whileLoop.position)
            return listOf(IAstModification.ReplaceNode(whileLoop, repeat, parent))
        } else if(constCondition==false) {
            errors.warn("condition is always false", whileLoop.condition.position)
            return listOf(IAstModification.Remove(whileLoop, parent as IStatementContainer))
        }


        /*
while CONDITION { STUFF }
    ==>
_whileloop:
  if not CONDITION goto _after
  STUFF
  goto _whileloop
_after:
         */
        val pos = whileLoop.position
        val loopLabel = program.makeLabel("whileloop", pos)
        val afterLabel = program.makeLabel("afterwhile", pos)
        val replacement = AnonymousScope(mutableListOf(
            loopLabel,
            IfElse(invertCondition(whileLoop.condition, program),
                AnonymousScope(mutableListOf(program.jumpLabel(afterLabel)), pos),
                AnonymousScope.empty(),
                pos),
            whileLoop.body,
            program.jumpLabel(loopLabel),
            afterLabel
        ), pos)
        return listOf(IAstModification.ReplaceNode(whileLoop, replacement, parent))
    }

    override fun before(functionCallStatement: FunctionCallStatement, parent: Node) =
        before(functionCallStatement as IFunctionCall, parent, functionCallStatement.position)

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node) =
        before(functionCallExpr as IFunctionCall, parent, functionCallExpr.position)

    private fun before(functionCall: IFunctionCall, parent: Node, position: Position): Iterable<IAstModification> {
        if(functionCall.target.nameInSource==listOf("peek")) {
            // peek(a) is synonymous with @(a)
            val memread = DirectMemoryRead(functionCall.args.single(), position)
            return listOf(IAstModification.ReplaceNode(functionCall as Node, memread, parent))
        }
        if(functionCall.target.nameInSource==listOf("poke")) {
            // poke(a, v) is synonymous with @(a) = v
            val tgt = AssignTarget(
                null,
                null,
                DirectMemoryWrite(functionCall.args[0], position),
                null,
                false,
                position = position
            )
            val assign = Assignment(tgt, functionCall.args[1], AssignmentOrigin.OPTIMIZER, position)
            return listOf(IAstModification.ReplaceNode(functionCall as Node, assign, parent))
        }
        return noModifications
    }

    override fun after(repeatLoop: RepeatLoop, parent: Node): Iterable<IAstModification> {
        if(repeatLoop.iterations==null) {
            // replace with a jump at the end, but make sure the jump is inserted *before* any subroutines that may occur inside this block
            val subroutineMovements = mutableListOf<IAstModification>()
            val subroutines = repeatLoop.body.statements.filterIsInstance<Subroutine>()
            subroutines.forEach { sub ->
                subroutineMovements += IAstModification.Remove(sub, sub.parent as IStatementContainer)
                subroutineMovements += IAstModification.InsertLast(sub, sub.parent as IStatementContainer)
            }

            val label = program.makeLabel("repeat", repeatLoop.position)
            val jump = program.jumpLabel(label)
            return listOf(
                IAstModification.InsertFirst(label, repeatLoop.body),
                IAstModification.InsertLast(jump, repeatLoop.body),
                IAstModification.ReplaceNode(repeatLoop, repeatLoop.body, parent)
            ) + subroutineMovements
        }
        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {
        // replace pointervar[word] by @(pointervar+word) to avoid the
        // "array indexing is limited to byte size 0..255" error for pointervariables.

        if(arrayIndexedExpression.pointerderef!=null) {
            return noModifications
        }

        val indexExpr = arrayIndexedExpression.indexer.indexExpr
        val arrayVar = arrayIndexedExpression.plainarrayvar!!.targetVarDecl()
        if(arrayVar!=null && (arrayVar.datatype.isUnsignedWord || (arrayVar.datatype.isPointer && arrayVar.datatype.sub==BaseDataType.UBYTE))) {
            val wordIndex = TypecastExpression(indexExpr, DataType.UWORD, true, indexExpr.position)
            val address = BinaryExpression(
                arrayIndexedExpression.plainarrayvar!!.copy(),
                "+",
                wordIndex,
                arrayIndexedExpression.position
            )
            return if (parent is AssignTarget) {
                // assignment to array
                val memwrite = DirectMemoryWrite(address, arrayIndexedExpression.position)
                val newtarget = AssignTarget(
                    null,
                    null,
                    memwrite,
                    null,
                    false,
                    position = arrayIndexedExpression.position
                )
                listOf(IAstModification.ReplaceNode(parent, newtarget, parent.parent))
            } else {
                // read from array
                val memread = DirectMemoryRead(address, arrayIndexedExpression.position)
                listOf(IAstModification.ReplaceNode(arrayIndexedExpression, memread, parent))
            }
        } else if(arrayVar!=null && (arrayVar.type==VarDeclType.MEMORY || arrayVar.datatype.isString || arrayVar.datatype.isPointer || arrayVar.datatype.isArray)) {
            return noModifications
        } else if(arrayVar!=null) {
            // it could be a pointer dereference instead of a simple array variable
            TODO("deref[word] rewrite ????  $arrayIndexedExpression")
//            val dt = arrayIndexedExpression.plainarrayvar!!.traverseDerefChainForDt(null)
//            if(dt.isUnsignedWord) {
//                // ptr.field[index] -->  @(ptr.field + index)
//                val index = arrayIndexedExpression.indexer.indexExpr
//                val address = BinaryExpression(arrayIndexedExpression.arrayvar.copy(), "+", index, arrayIndexedExpression.position)
//                if(parent is AssignTarget) {
//                    val memwrite = DirectMemoryWrite(address, arrayIndexedExpression.position)
//                    return listOf(IAstModification.ReplaceNode(arrayIndexedExpression, memwrite, parent))
//                } else {
//                    val memread = DirectMemoryRead(address, arrayIndexedExpression.position)
//                    return listOf(IAstModification.ReplaceNode(arrayIndexedExpression, memread, parent))
//                }
//            }
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        fun isStringComparison(leftDt: InferredTypes.InferredType, rightDt: InferredTypes.InferredType): Boolean =
            if(leftDt issimpletype BaseDataType.STR && rightDt issimpletype BaseDataType.STR)
                true
            else
                leftDt issimpletype BaseDataType.UWORD && rightDt issimpletype BaseDataType.STR || leftDt issimpletype BaseDataType.STR && rightDt issimpletype BaseDataType.UWORD

        if(expr.operator=="in") {
            val containment = ContainmentCheck(expr.left, expr.right, expr.position)
            return listOf(IAstModification.ReplaceNode(expr, containment, parent))
        }

        if(expr.operator in ComparisonOperators) {
            val leftDt = expr.left.inferType(program)
            val rightDt = expr.right.inferType(program)

            if(isStringComparison(leftDt, rightDt)) {
                // replace string comparison expressions with calls to string.compare()
                val stringCompare = FunctionCallExpression(
                    IdentifierReference(listOf("prog8_lib_stringcompare"), expr.position),
                    mutableListOf(expr.left.copy(), expr.right.copy()), expr.position)
                val zero = NumericLiteral.optimalInteger(0, expr.position)
                val comparison = BinaryExpression(stringCompare, expr.operator, zero, expr.position)
                return listOf(IAstModification.ReplaceNode(expr, comparison, parent))
            }
        }

        if(expr.operator=="*" && expr.inferType(program).isInteger && expr.left isSameAs expr.right) {
            // replace squaring with call to builtin function to do this in a more optimized way
            val function = if(expr.left.inferType(program).isBytes) "prog8_lib_square_byte" else "prog8_lib_square_word"
            val squareCall = FunctionCallExpression(
                IdentifierReference(listOf(function), expr.position),
                mutableListOf(expr.left.copy()), expr.position)
            return listOf(IAstModification.ReplaceNode(expr, squareCall, parent))
        }

        if(expr.operator==".") {
            val left = expr.left as? ArrayIndexedExpression
            val right = expr.right as? PtrDereference
            if(left!=null && right!=null) {
                if(parent is BinaryExpression && parent.operator=="." && parent.right===expr) {
                    val parentLeft = parent.left as? IdentifierReference
                    if(parentLeft!=null) {
                        // parent is:
                        //         BinaryExpression "."
                        //          /              \
                        //      IdRef            (this BinExpr)
                        //       x.y               /         \
                        //                    ArrayIdx      PtrDeref
                        //                     z[i]           field
                        //
                        // transform this into this so it can be processed further:
                        //
                        //         BinaryExpression "."
                        //          /             \
                        //      ArrayIdx         IdentifierRef
                        //       x.y.z[i]           field

                        val combinedIdentifier = IdentifierReference(parentLeft.nameInSource+left.plainarrayvar!!.nameInSource, parentLeft.position)
                        val newleft = ArrayIndexedExpression(combinedIdentifier, null, left.indexer, left.position)
                        val newright = IdentifierReference(listOf(right.chain.single()), right.position)
                        return listOf(
                            IAstModification.ReplaceNode(parent.left, newleft, parent),
                            IAstModification.ReplaceNode(parent.right, newright, parent)
                        )
                    }
                }
            }

            if(expr.left is ArrayIndexedExpression && right!=null) {
                // replace  replace x.y.listarray[2]^^.value    with  just  x.y.listarray[2] . value
                // this will be further modified elsewhere
                val ident = IdentifierReference(right.chain, right.position)
                return listOf(IAstModification.ReplaceNode(expr.right, ident, expr))

                // TODO hmmm , replace cx16.r1 = listarray[2]^^.value   with   a temp pointer var to contain the indexed value
//                val assign = expr.parent as? Assignment
//                if(assign!=null) {
//                    val ptrDt = expr.left.inferType(program).getOrUndef()
//                    val pointerVar = VarDecl.createAuto(ptrDt)
//                    val pointerIdent = IdentifierReference(pointerVar.name.split("."), expr.position)
//                    val tgt = AssignTarget(pointerIdent, null, null, null, false, null, position = expr.position)
//                    val assignPtr = Assignment(tgt, expr.left, AssignmentOrigin.USERCODE, expr.position)
//                    val derefValue = PtrDereference(pointerIdent.nameInSource + right.chain, false, expr.position)
//                    return listOf(
//                        IAstModification.InsertBefore(assign, assignPtr, assign.parent as IStatementContainer),
//                        IAstModification.ReplaceNode(assign.value, derefValue, assign),
//                        IAstModification.InsertFirst(pointerVar, expr.definingSubroutine!!)
//                    )
//                }
            }
        }

        return noModifications
    }

    override fun after(memread: DirectMemoryRead, parent: Node): Iterable<IAstModification> {
        // for word variables:
        // @(&var) --> lsb(var)
        // @(&var+1) --> msb(var)           NOTE: ONLY WHEN VAR IS AN ACTUAL WORD VARIABLE (POINTER)

        val addrOf = memread.addressExpression as? AddressOf
        if(addrOf?.arrayIndex!=null)
            return noModifications
        if(addrOf!=null && addrOf.identifier?.inferType(program)?.isWords==true) {
            val lsb = FunctionCallExpression(IdentifierReference(listOf("lsb"), memread.position), mutableListOf(addrOf.identifier!!), memread.position)
            return listOf(IAstModification.ReplaceNode(memread, lsb, parent))
        }
        val expr = memread.addressExpression as? BinaryExpression
        if(expr!=null && expr.operator=="+") {
            val addressOf = expr.left as? AddressOf
            val offset = (expr.right as? NumericLiteral)?.number?.toInt()
            if(addressOf!=null && offset==1) {
                val variable = addressOf.identifier?.targetVarDecl()
                if(variable!=null && variable.datatype.isWord) {
                    val msb = FunctionCallExpression(IdentifierReference(listOf("msb"), memread.position), mutableListOf(
                        addressOf.identifier!!
                    ), memread.position)
                    return listOf(IAstModification.ReplaceNode(memread, msb, parent))
                }
            }
        }

        return noModifications
    }

    override fun after(chainedAssignment: ChainedAssignment, parent: Node): Iterable<IAstModification> {
        val assign = chainedAssignment.nested as? Assignment
        if(assign!=null) {
            // unpack starting from last in the chain
            val assigns = mutableListOf<Statement>(assign)
            var lastChained: ChainedAssignment = chainedAssignment
            var pc: ChainedAssignment? = chainedAssignment

            if(assign.value.isSimple) {
                // simply copy the RHS value to each component's assignment
                while (pc != null) {
                    lastChained = pc
                    assigns.add(Assignment(pc.target.copy(), assign.value.copy(), assign.origin, pc.position))
                    pc = pc.parent as? ChainedAssignment
                }
            } else if(pc!=null) {
                // need to evaluate RHS once and reuse that in each component's assignment
                val firstComponentAsValue = assign.target.toExpression()
                while (pc != null) {
                    lastChained = pc
                    assigns.add(Assignment(pc.target.copy(), firstComponentAsValue.copy(), assign.origin, pc.position))
                    pc = pc.parent as? ChainedAssignment
                }
            }
            return listOf(IAstModification.ReplaceNode(lastChained,
                AnonymousScope(assigns, chainedAssignment.position), lastChained.parent))
        }
        return noModifications
    }

    override fun after(whenChoice: WhenChoice, parent: Node): Iterable<IAstModification> {
        // replace a range expression in a when by the actual list of numbers it represents
        val values = whenChoice.values
        if(values!=null && values.size==1) {
            val conditionType = (whenChoice.parent as When).condition.inferType(program)
            val intRange = (values[0] as? RangeExpression)?.toConstantIntegerRange()
            if(conditionType.isKnown && intRange != null) {
                if(intRange.count()>255)
                    errors.err("values list too long", values[0].position)
                else {
                    val dt = conditionType.getOrUndef().base
                    val newValues = intRange.map {
                        val num = NumericLiteral(BaseDataType.LONG, it.toDouble(), values[0].position)
                        num.linkParents(whenChoice)
                        val cast = num.cast(dt, true)
                        if (cast.isValid) cast.valueOrZero() else null
                    }
                    if(null !in newValues) {
                        if(newValues.size>=10)
                            errors.warn("long list of values, checking will not be very efficient", values[0].position)
                        values.clear()
                        for(num in newValues)
                            values.add(num!!)
                    }
                }
            }
        }

        return noModifications
    }

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {

        if (identifier.nameInSource.size>1) {
            val firstTarget = (identifier.firstTarget() as? VarDecl)
            val firstDt = firstTarget?.datatype
            if (firstDt?.isPointer == true) {
                // the a.b.c.d can be a pointer dereference chain ending in a struct field;  a^^.b^^.c^^.d

                val chain = mutableListOf(identifier.nameInSource[0])
                var struct = firstDt.subType as? StructDecl
                for(name in identifier.nameInSource.drop(1)) {
                    if(struct==null) {
                        errors.err("unknown field '${name}", position = identifier.position)
                        return noModifications
                    }
                    val fieldDt = struct.getFieldType(name)
                    if(fieldDt==null) {
                        errors.err("unknown field '${name}' in struct '${struct.name}'", identifier.position)
                        return noModifications
                    }
                    if(fieldDt.isPointer) {
                        chain.add(name)
                        struct = fieldDt.subType as? StructDecl
                    } else {
                        chain.add(name)
                        struct = null
                    }
                }
                val deref = PtrDereference(chain, false, identifier.position)
                return listOf(IAstModification.ReplaceNode(identifier, deref, parent))
            }
        }
        return noModifications
    }

    override fun after(ongoto: OnGoto, parent: Node): Iterable<IAstModification> {
        val indexDt = ongoto.index.inferType(program).getOrUndef()
        if(!indexDt.isUnsignedByte)
            return noModifications

        val numlabels = ongoto.labels.size
        val split = if(ongoto.isCall)
            true    // for calls (indirect JSR), split array is always the optimal choice
        else
            target.cpu==CpuType.CPU6502    // for goto (indirect JMP), split array is optimal for 6502, but NOT for the 65C02 (it has a different JMP addressing mode available)
        val arrayDt = DataType.arrayFor(BaseDataType.UWORD, split)
        val labelArray = ArrayLiteral(InferredTypes.knownFor(arrayDt), ongoto.labels.toTypedArray(), ongoto.position)
        val jumplistArray = VarDecl.createAutoOptionalSplit(labelArray)

        val indexValue: Expression
        val conditionVar: VarDecl?
        val assignIndex: Assignment?

        // put condition in temp var, if it is not simple; to avoid evaluating expression multiple times
        if (ongoto.index.isSimple) {
            indexValue = ongoto.index
            assignIndex = null
            conditionVar = null
        } else {
            conditionVar = VarDecl.createAuto(indexDt)
            indexValue = IdentifierReference(listOf(conditionVar.name), conditionVar.position)
            val varTarget = AssignTarget(indexValue, null, null, null, false, position=conditionVar.position)
            assignIndex = Assignment(varTarget, ongoto.index, AssignmentOrigin.USERCODE, ongoto.position)
        }

        val callTarget = ArrayIndexedExpression(IdentifierReference(listOf(jumplistArray.name), jumplistArray.position), null, ArrayIndex(indexValue.copy(), indexValue.position), ongoto.position)
        val callIndexed = AnonymousScope.empty(ongoto.position)
        if(ongoto.isCall) {
            callIndexed.statements.add(FunctionCallStatement(IdentifierReference(listOf("call"), ongoto.position), mutableListOf(callTarget), true, ongoto.position))
        } else {
            callIndexed.statements.add(Jump(callTarget, ongoto.position))
        }

        val ifSt = if(ongoto.elsepart==null || ongoto.elsepart!!.isEmpty()) {
            // if index<numlabels call(labels[index])
            val compare = BinaryExpression(indexValue.copy(), "<", NumericLiteral.optimalInteger(numlabels, ongoto.position), ongoto.position)
            IfElse(compare, callIndexed, AnonymousScope.empty(), ongoto.position)
        } else {
            // if index>=numlabels elselabel() else call(labels[index])
            val compare = BinaryExpression(indexValue.copy(), ">=", NumericLiteral.optimalInteger(numlabels, ongoto.position), ongoto.position)
            IfElse(compare, ongoto.elsepart!!, callIndexed, ongoto.position)
        }

        val replacementScope = AnonymousScope(if(conditionVar==null)
                mutableListOf(ifSt, jumplistArray)
            else
                mutableListOf(conditionVar, assignIndex!!, ifSt, jumplistArray)
            , ongoto.position)
        return listOf(IAstModification.ReplaceNode(ongoto, replacementScope, parent))
    }

    override fun after(deref: PtrDereference, parent: Node): Iterable<IAstModification> {
        val isLHS = parent is AssignTarget
        val varDt = (deref.firstTarget() as? VarDecl)?.datatype
        if(varDt?.isUnsignedWord==true || (varDt?.isPointer==true && varDt.sub?.isByte==true)) {
            // replace  ptr^^   by  @(ptr)    when ptr is uword or ^^byte
            val identifier = IdentifierReference(deref.chain, deref.position)
            if(isLHS && varDt.sub==BaseDataType.UBYTE) {
                val memwrite = DirectMemoryWrite(identifier, deref.position)
                return listOf(IAstModification.ReplaceNode(deref, memwrite, parent))
            } else if(!isLHS) {
                val memread = DirectMemoryRead(identifier, deref.position)
                val replacement = if (varDt.sub == BaseDataType.BYTE)
                    TypecastExpression(memread, DataType.BYTE, true, memread.position)
                else
                    memread
                return listOf(IAstModification.ReplaceNode(deref, replacement, parent))
            }
        }

        val expr = deref.parent as? BinaryExpression
        if (expr != null && expr.operator == ".") {
            if (expr.left is IdentifierReference && expr.right === deref) {
                // replace  (a) . (b^^)  by (a.b)^^
                val name = (expr.left as IdentifierReference).nameInSource + deref.chain
                val replacement = PtrDereference(name, deref.derefLast, deref.position)
                return listOf(IAstModification.ReplaceNode(expr, replacement, expr.parent))
            } else if(expr.left===deref && expr.right is ArrayIndexedExpression) {
                // replace  (a^^) . ( s[b] )  by  (a^^.s^^)[b]
                val idx = expr.right as ArrayIndexedExpression
                if(idx.plainarrayvar!=null) {
                    val name = deref.chain + idx.plainarrayvar!!.nameInSource
                    val ptrDeref = PtrDereference(name, false, deref.position)
                    val indexer = ArrayIndexedExpression(null, ptrDeref, idx.indexer, idx.position)
                    return listOf(IAstModification.ReplaceNode(expr, indexer, expr.parent))
                } else {
                    TODO("convert ptr.p[idx]")
                }
            }
        }

        return noModifications
    }

    override fun after(deref: ArrayIndexedPtrDereference, parent: Node): Iterable<IAstModification> {
        // get rid of the ArrayIndexedPtrDereference AST node, replace it with other AST nodes that are equivalent

        if(deref.chain.last().second!=null && deref.derefLast && deref.chain.dropLast(1).all( { it.second==null } )) {

            // parent could be Assigment directly, or a binexpr chained pointer expression (with '.' operator)_
            if(parent is Assignment) {
                val dt = deref.inferType(program).getOrUndef()
                require(dt.isNumericOrBool)
                if (parent.value isSameAs deref) {
                    // x = z[i]^^ -->  peekX(z[i])
                    val (peekFunc, cast) =
                        if(dt.isBool) "peekbool" to null
                        else if (dt.isUnsignedByte) "peek" to null
                        else if (dt.isSignedByte) "peek" to DataType.BYTE
                        else if (dt.isUnsignedWord) "peekw" to null
                        else if (dt.isSignedWord) "peekw" to DataType.WORD
                        else if (dt.isLong) "peekl" to null
                        else if (dt.isFloat) "peekf" to null
                        else throw FatalAstException("can only deref a numeric or boolean pointer here")
                    val indexer = deref.chain.last().second!!
                    val identifier = IdentifierReference(deref.chain.map { it.first }, deref.position)
                    val indexed = ArrayIndexedExpression(identifier, null, indexer, deref.position)
                    val peekIdent = IdentifierReference(listOf(peekFunc), deref.position)
                    val peekCall = FunctionCallExpression(peekIdent, mutableListOf(indexed), deref.position)
                    if(cast==null)
                        return listOf(IAstModification.ReplaceNode(parent.value, peekCall, parent))
                    else {
                        val casted = TypecastExpression(peekCall, cast, true, deref.position)
                        return listOf(IAstModification.ReplaceNode(parent.value, casted, parent))
                    }
                }
            } else if(parent is BinaryExpression && parent.operator==".") {
                val left = parent.left as? IdentifierReference
                val right = parent.right as? ArrayIndexedPtrDereference
                if(left!=null && right!=null) {
                    if(right.chain.last().second!=null && right.derefLast && right.chain.dropLast(1).all { it.second!=null }) {
                        // (a.b.c) . (d[i]^^)  --> a.b.c.d[i]^^
                        val combinedIdentifier = left.nameInSource+right.chain.map { it.first }
                        val chain: List<Pair<String, ArrayIndex?>> = combinedIdentifier.dropLast(1).map { it to null } + (combinedIdentifier.last() to right.chain.last().second)
                        val deref = ArrayIndexedPtrDereference(chain,true, right.position)
                        return listOf(IAstModification.ReplaceNode(parent, deref, parent.parent))
                    }
                }
                val dt = parent.inferType(program).getOrUndef()
                TODO("$dt")
            }
            else {
                // ????
                // z[i]^^ = value -->  pokeX(z[i], value)
                val dt = deref.inferType(program).getOrUndef()
                TODO("$dt")
            }
        }


        val firstIndexed = deref.chain.indexOfFirst { it.second!=null }
        if(firstIndexed == 0 && deref.chain.size>1) {
            // z[i]^^.field   -->  (z[i]) . (field)

            val index = deref.chain.first()
            val tail = deref.chain.drop(1)
            if (tail.any { it.second != null }) {
                TODO("support multiple array indexed dereferencings  ${deref.position}")
            } else {
                val pointer = IdentifierReference(listOf(index.first), deref.position)
                val left = ArrayIndexedExpression(pointer, null, index.second!!, deref.position)
                val right = PtrDereference(tail.map { it.first }, deref.derefLast, deref.position)
                val derefExpr = BinaryExpression(left, ".", right, deref.position)
                return listOf(IAstModification.ReplaceNode(deref, derefExpr, parent))
            }
        }

        TODO("convert yet another array indexed dereference $deref   ${deref.position}")
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        subroutine.returntypes.withIndex().forEach { (idx, rt) ->
            if(rt.isPointer && rt.sub==BaseDataType.STR) {
                errors.info("^^str replaced by ^^ubyte in return type(s)", subroutine.position)
                subroutine.returntypes[idx] = DataType.pointer(BaseDataType.UBYTE)
            }
        }

        subroutine.parameters.withIndex().forEach { (idx, param) ->
            if(param.type.isPointer && param.type.sub==BaseDataType.STR) {
                errors.info("^^str replaced by ^^ubyte", param.position)
                subroutine.parameters[idx] = SubroutineParameter(param.name, DataType.pointer(BaseDataType.UBYTE), param.zp, param.registerOrPair, param.position)
            }
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        val targetDt = assignment.target.inferType(program)
        val sourceDt = assignment.value.inferType(program)
        if(targetDt.isStructInstance && sourceDt.isStructInstance) {
            if(targetDt == sourceDt) {
                val size = program.memsizer.memorySize(sourceDt.getOrUndef(), null)
                require(program.memsizer.memorySize(targetDt.getOrUndef(), null)==size)
                val sourcePtr = IdentifierReference((assignment.value as PtrDereference).chain, assignment.position)
                val targetPtr = IdentifierReference(assignment.target.pointerDereference!!.chain, assignment.position)
                val numBytes = NumericLiteral.optimalInteger(size, assignment.position)
                val memcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "memcopy"), assignment.position),
                    mutableListOf(sourcePtr, targetPtr, numBytes),
                    false, assignment.position)
                return listOf(IAstModification.ReplaceNode(assignment, memcopy, parent))
            }
        }

        return noModifications
    }
}
