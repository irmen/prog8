package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.*
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
    // - convert on..goto/call to jumpaddr array and separate goto/call
    // - replace implicit pointer dereference chains (a.b.c.d) with explicit ones (a^^.b^^.c^^.d)
    // - replace ptr^^ by @(ptr) if ptr is just an uword.
    // - replace p1^^ = p2^^  by memcopy.

    private val globalReservedSlabs = mutableMapOf<String, MemorySlabReservation>()

    override fun before(program: Program): Iterable<AstModification> {
        globalReservedSlabs.clear()
        // Pre-collect existing reservations from the entire program to avoid duplicates in multiple passes
        val collector = object : IAstVisitor {
            override fun visit(reservation: MemorySlabReservation) {
                globalReservedSlabs[reservation.slabName] = reservation
            }
        }
        program.modules.forEach { module ->
            module.accept(collector)
        }
        return super.before(program)
    }

    override fun before(breakStmt: Break, parent: Node): Iterable<AstModification> {
        fun jumpAfter(stmt: Statement): Iterable<AstModification> {
            val label = program.makeLabel("after", breakStmt.position)
            return listOf(
                AstReplaceNode(breakStmt, program.jumpLabel(label), parent),
                AstInsert.after(stmt, label, stmt.parent as IStatementContainer)
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

    override fun before(continueStmt: Continue, parent: Node): Iterable<AstModification> {
        fun jumpToBottom(scope: IStatementContainer): Iterable<AstModification> {
            val label = program.makeLabel("cont", continueStmt.position)
            return listOf(
                AstReplaceNode(continueStmt, program.jumpLabel(label), parent),
                AstInsert.last(scope, label)
            )
        }

        fun jumpToBefore(loop: WhileLoop): Iterable<AstModification> {
            val label = program.makeLabel("cont", continueStmt.position)
            return listOf(
                AstReplaceNode(continueStmt, program.jumpLabel(label), parent),
                AstInsert.before(loop, label, loop.parent as IStatementContainer)
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

    override fun after(untilLoop: UntilLoop, parent: Node): Iterable<AstModification> {
        /*
do { STUFF } until CONDITION
    ===>
_loop:
  STUFF
if not CONDITION
   goto _loop
         */
        val error = checkCondition(untilLoop.condition)
        if(error!=null) {
            errors.err(error, untilLoop.condition.position)
            return noModifications
        }
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
        return listOf(AstReplaceNode(untilLoop, replacement, parent))
    }

    override fun after(expr: PrefixExpression, parent: Node): Iterable<AstModification> {
        val dt = expr.expression.inferType(program).getOrUndef()
        if(dt.isPointerArray || dt.isPointer) {
            errors.err("pointers don't support prefix operators", expr.position)
        }

        return noModifications
    }

    private fun checkCondition(condition: Expression): String? {
        if(!condition.inferType(program).isBool)
            return "condition should be a boolean"
        val cast = condition as? TypecastExpression
        if(cast!=null && cast.type.isBool) {
            if(cast.expression.inferType(program).isPointer) {
                return "condition should be a boolean"
            }
        }
        return null
    }

    override fun after(whileLoop: WhileLoop, parent: Node): Iterable<AstModification> {

        /*
        while true -> repeat
        while false -> discard
         */

        val error = checkCondition(whileLoop.condition)
        if(error!=null) {
            errors.err(error, whileLoop.condition.position)
            return noModifications
        }

        val constCondition = whileLoop.condition.constValue(program)?.asBooleanValue
        if(constCondition==true) {
            errors.warn("condition is always true", whileLoop.condition.position)
            val repeat = RepeatLoop(null, whileLoop.body, whileLoop.position)
            return listOf(AstReplaceNode(whileLoop, repeat, parent))
        } else if(constCondition==false) {
            errors.warn("condition is always false", whileLoop.condition.position)
            return listOf(AstRemove(whileLoop, parent as IStatementContainer))
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
        return listOf(AstReplaceNode(whileLoop, replacement, parent))
    }

    override fun before(functionCallStatement: FunctionCallStatement, parent: Node) =
        before(functionCallStatement as IFunctionCall, parent, functionCallStatement.position)

    override fun before(functionCallExpr: FunctionCallExpression, parent: Node) =
        before(functionCallExpr as IFunctionCall, parent, functionCallExpr.position)

    private fun before(functionCall: IFunctionCall, parent: Node, position: Position): Iterable<AstModification> {
        val outerFunc = functionCall.target.nameInSource

        if(outerFunc==listOf("peek")) {
            // peek(a) is synonymous with @(a)
            val memread = DirectMemoryRead(functionCall.args.single(), position)
            return listOf(AstReplaceNode(functionCall as Node, memread, parent))
        }
        if(outerFunc==listOf("poke") && parent !is Assignment) {
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
            return listOf(AstReplaceNode(functionCall as Node, assign, parent))
        }

        if(outerFunc==listOf("pokew") || outerFunc==listOf("pokel") || outerFunc==listOf("pokef")) {
            val innercall = functionCall.args[1] as? IFunctionCall
            val innerFunc = innercall?.target?.nameInSource
            val peekname = "peek" + outerFunc[0].substring(4)
            if(innerFunc==listOf(peekname)) {
                val targetAddress = functionCall.args[0]
                val sourceAddress = innercall.args[0]
                val targetDt = targetAddress.inferType(program).getOrUndef().sub
                if(targetDt!=null) {
                    val copy = when {
                        targetDt.isLong ->
                            FunctionCallStatement(
                                IdentifierReference(listOf("prog8_lib_copylong"), position),
                                mutableListOf(sourceAddress, targetAddress), false, position
                            )
                        targetDt.isFloat ->
                            FunctionCallStatement(
                                IdentifierReference(listOf("prog8_lib_copyfloat"), position),
                                mutableListOf(sourceAddress, targetAddress), false, position
                            )
                        else -> null
                    }

                    if(copy!=null)
                        return listOf(AstReplaceNode(functionCall as Node, copy, parent))
                }
            }
        }

        if(outerFunc==listOf("pushp")) {
            // pushp adapts to pointer size: pushw on 16-bit targets, pushl on 32-bit
            val pushName = if(target.POINTER_MEM_SIZE > 2u) "pushl" else "pushw"
            val newCall = FunctionCallStatement(
                IdentifierReference(listOf(pushName), position),
                functionCall.args, false, position
            )
            return listOf(AstReplaceNode(functionCall as Node, newCall, parent))
        }
        if(outerFunc==listOf("popp")) {
            val popName = if(target.POINTER_MEM_SIZE > 2u) "popl" else "popw"
            val newCall = FunctionCallExpression(
                IdentifierReference(listOf(popName), position),
                mutableListOf(), position
            )
            return listOf(AstReplaceNode(functionCall as Node, newCall, parent))
        }
        if(outerFunc==listOf("pokep")) {
            val pokeName = if(target.POINTER_MEM_SIZE > 2u) "pokel" else "pokew"
            val newCall = FunctionCallStatement(
                IdentifierReference(listOf(pokeName), position),
                functionCall.args, false, position
            )
            return listOf(AstReplaceNode(functionCall as Node, newCall, parent))
        }

        return noModifications
    }

    private fun sanitizeSlabName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<AstModification> {
        if (functionCallExpr.isMemoryRefCall) {
            val str = functionCallExpr.args[0] as? StringLiteral
            if (str == null) {
                errors.err("memory name argument must be a string literal", functionCallExpr.args[0].position)
                return noModifications
            } else if (str.value.isEmpty()) {
                errors.err("memory name argument cannot be empty string", functionCallExpr.args[0].position)
                return noModifications
            }
            val slabName = sanitizeSlabName(str.value)
            val ref = MemorySlabRef(slabName, functionCallExpr.position)
            return listOf(AstReplaceNode(functionCallExpr, ref, parent))
        }

        if (functionCallExpr.isMemoryCall) {
            val str = functionCallExpr.args[0] as? StringLiteral
            if (str == null) {
                errors.err("memory name argument must be a string literal", functionCallExpr.args[0].position)
                return noModifications
            } else if (str.value.isEmpty()) {
                errors.err("memory name argument cannot be empty string", functionCallExpr.args[0].position)
                return noModifications
            }

            val sizeNum = (functionCallExpr.args[1] as? NumericLiteral)?.number?.toInt()
            val alignNum = (functionCallExpr.args[2] as? NumericLiteral)?.number?.toInt()
            if (sizeNum == null) {
                errors.err("argument must be a constant", functionCallExpr.args[1].position)
                return noModifications
            }
            if (alignNum == null) {
                errors.err("argument must be a constant", functionCallExpr.args[2].position)
                return noModifications
            } else if (alignNum != 0 && (alignNum !in 0..256 || (alignNum and (alignNum - 1)) != 0)) {
                errors.err("alignment must be 0 or a power of 2 (max 256)", functionCallExpr.args[2].position)
                return noModifications
            }

            val slabName = sanitizeSlabName(str.value)
            val size = sizeNum.toUInt()
            val align = alignNum.toUInt()

            val reservation = MemorySlabReservation(slabName, size, align, functionCallExpr.position)
            val ref = MemorySlabRef(slabName, functionCallExpr.position)

            val containingStmt = findContainingStatement(functionCallExpr)
            val container = containingStmt.parent as IStatementContainer
            
            val mods = mutableListOf<AstModification>(AstReplaceNode(functionCallExpr, ref, parent))
            val existing = globalReservedSlabs[slabName]
            if (existing == null || existing.size != size || existing.align != align) {
                mods.add(AstInsert.before(containingStmt, reservation, container))
                globalReservedSlabs[slabName] = reservation
            }
            return mods
        }
        if(functionCallExpr.target.nameInSource==listOf("peekp")) {
            val peekName = if(target.POINTER_MEM_SIZE > 2u) "peekl" else "peekw"
            val newCall = FunctionCallExpression(
                IdentifierReference(listOf(peekName), functionCallExpr.position),
                functionCallExpr.args.toMutableList(), functionCallExpr.position
            )
            return listOf(AstReplaceNode(functionCallExpr, newCall, parent))
        }
        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<AstModification> {
        if (functionCallStatement.isMemoryRefCall) {
            val container = parent as IStatementContainer
            return listOf(AstRemove(functionCallStatement, container))
        }

        if (functionCallStatement.isMemoryCall) {
            val str = functionCallStatement.args[0] as? StringLiteral
            if (str == null) {
                errors.err("memory name argument must be a string literal", functionCallStatement.args[0].position)
                return noModifications
            } else if (str.value.isEmpty()) {
                errors.err("memory name argument cannot be empty string", functionCallStatement.args[0].position)
                return noModifications
            }

            val sizeNum = (functionCallStatement.args[1] as? NumericLiteral)?.number?.toInt()
            val alignNum = (functionCallStatement.args[2] as? NumericLiteral)?.number?.toInt()
            if (sizeNum == null) {
                errors.err("argument must be a constant", functionCallStatement.args[1].position)
                return noModifications
            }
            if (alignNum == null) {
                errors.err("argument must be a constant", functionCallStatement.args[2].position)
                return noModifications
            } else if (alignNum != 0 && (alignNum !in 0..256 || (alignNum and (alignNum - 1)) != 0)) {
                errors.err("alignment must be 0 or a power of 2 (max 256)", functionCallStatement.args[2].position)
                return noModifications
            }

            val slabName = sanitizeSlabName(str.value)
            val size = sizeNum.toUInt()
            val align = alignNum.toUInt()

            val container = parent as IStatementContainer
            val existing = globalReservedSlabs[slabName]
            return if (existing != null && existing.size == size && existing.align == align) {
                listOf(AstRemove(functionCallStatement, container))
            } else {
                val reservation = MemorySlabReservation(slabName, size, align, functionCallStatement.position)
                globalReservedSlabs[slabName] = reservation
                listOf(AstReplaceNode(functionCallStatement, reservation, parent))
            }
        }
        return noModifications
    }

    private fun findContainingStatement(node: Node): Statement {
        var n = node
        while (n !is Statement) n = n.parent
        return n
    }

    override fun after(repeatLoop: RepeatLoop, parent: Node): Iterable<AstModification> {
        if(repeatLoop.iterations==null) {
            // replace with a jump at the end, but make sure the jump is inserted *before* any subroutines that may occur inside this block
            val subroutineMovements = mutableListOf<AstModification>()
            val subroutines = repeatLoop.body.statements.filterIsInstance<Subroutine>()
            subroutines.forEach { sub ->
                subroutineMovements += AstRemove(sub, sub.parent as IStatementContainer)
                subroutineMovements += AstInsert.last(sub.parent as IStatementContainer, sub)
            }

            val label = program.makeLabel("repeat", repeatLoop.position)
            val jump = program.jumpLabel(label)
            return listOf(
                AstInsert.first(repeatLoop.body, label),
                AstInsert.last(repeatLoop.body, jump),
                AstReplaceNode(repeatLoop, repeatLoop.body, parent)
            ) + subroutineMovements
        }
        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<AstModification> {
        // Handle 2D array indexing: matrix[row][col] -> matrix[row * numCols + col]
        // This must be done FIRST, before any other array transformations
        if(arrayIndexedExpression.nestedArray != null) {
            val nested = arrayIndexedExpression.nestedArray!!
            val outerIndex = arrayIndexedExpression.indexer.indexExpr

            // Check for 3D+ indexing
            if(nested.nestedArray != null) {
                errors.err("3D or higher array indexing is not supported", arrayIndexedExpression.position)
                return noModifications
            }

            // Find the variable declaration
            val targetVarDecl = nested.plainarrayvar?.targetStatement(program.builtinFunctions) as? VarDecl

            if(targetVarDecl == null) {
                // Complex expression - can't determine dimensions, report error
                errors.err("chained indexing requires the variable to be declared as a 2D array", arrayIndexedExpression.position)
                return noModifications
            }

            if(!targetVarDecl.is2DArray) {
                errors.err("chained indexing requires the variable to be declared as a 2D array", arrayIndexedExpression.position)
                return noModifications
            }

            val numCols = targetVarDecl.matrixNumCols ?: return noModifications
            val innerIndex = nested.indexer.indexExpr

            // Calculate: row * numCols + col
            val rowTimesCols = BinaryExpression(innerIndex, "*", numCols.copy(), arrayIndexedExpression.position)
            val flatIndex = BinaryExpression(rowTimesCols, "+", outerIndex.copy(), arrayIndexedExpression.position)

            // Create flattened ArrayIndexedExpression
            val flatArrayIndex = ArrayIndex(flatIndex, arrayIndexedExpression.position)
            val desugared = ArrayIndexedExpression(
                nested.plainarrayvar?.copy(),
                null,  // No more nesting
                nested.pointerderef?.copy(),
                flatArrayIndex,
                arrayIndexedExpression.position
            )
            return listOf(AstReplaceNode(arrayIndexedExpression, desugared, parent))
        }

        // replace pointervar[word] by @(pointervar+word) to avoid the
        // "array indexing is limited to byte size 0..255" error for pointervariables.
        // (uses pokew or pokef if the pointer is a word or float pointer).

        if(arrayIndexedExpression.pointerderef!=null) {
            return noModifications
        }

        val indexExpr = arrayIndexedExpression.indexer.indexExpr
        val arrayVar = arrayIndexedExpression.plainarrayvar!!.targetVarDecl()
        // uword (16-bit) or long (32-bit) variables can hold a pointer value and can be indexed as such
        val isUwordPointerHolder = arrayVar!=null && arrayVar.datatype.isUnsignedWord && target.POINTER_MEM_SIZE <= 2u
        val isLongPointerHolder = arrayVar!=null && arrayVar.datatype.isLong && target.POINTER_MEM_SIZE > 2u
        if(arrayVar!=null && (isUwordPointerHolder || isLongPointerHolder || arrayVar.datatype.isPointer)) {
            val indexType = target.pointerType
            val wordIndex = TypecastExpression(indexExpr, indexType, true, indexExpr.position)
            val address = BinaryExpression(
                arrayIndexedExpression.plainarrayvar!!.copy(),
                "+",
                wordIndex,
                arrayIndexedExpression.position
            )
            if(isUwordPointerHolder || isLongPointerHolder || arrayVar.datatype.sub?.isByte==true) {
                return if (parent is AssignTarget) {
                    // assignment to array
                    val memwrite = DirectMemoryWrite(address, arrayIndexedExpression.position)
                    val newtarget = AssignTarget(null, null, memwrite, null, false, position = arrayIndexedExpression.position)
                    listOf(AstReplaceNode(parent, newtarget, parent.parent))
                } else {
                    // read from array
                    val memread = DirectMemoryRead(address, arrayIndexedExpression.position)
                    val replacement = if(arrayVar.datatype.sub?.isSigned==true)
                            TypecastExpression(memread, DataType.BYTE, true, memread.position)
                        else
                            memread
                    listOf(AstReplaceNode(arrayIndexedExpression, replacement, parent))
                }
            } else if(arrayVar.datatype.sub?.isWord==true) {
                // use peekw/pokew
                if(parent is AssignTarget) {
                    val assignment = parent.parent as Assignment
                    val args = mutableListOf(address, assignment.value)
                    val poke = FunctionCallStatement(IdentifierReference(listOf("pokew"), arrayIndexedExpression.position), args, false, arrayIndexedExpression.position)
                    return listOf(AstReplaceNode(assignment, poke, assignment.parent))
                } else {
                    val peek = FunctionCallExpression(IdentifierReference(listOf("peekw"), arrayIndexedExpression.position), mutableListOf(address), arrayIndexedExpression.position)
                    val replacement = if(arrayVar.datatype.sub?.isSigned==true)
                            TypecastExpression(peek, DataType.WORD, true, peek.position)
                        else
                            peek
                    return listOf(AstReplaceNode(arrayIndexedExpression, replacement, parent))
                }
            } else if(arrayVar.datatype.sub==BaseDataType.BOOL) {
                // use peekbool/pokebool
                if(parent is AssignTarget) {
                    val assignment = parent.parent as Assignment
                    val args = mutableListOf(address, assignment.value)
                    val poke = FunctionCallStatement(IdentifierReference(listOf("pokebool"), arrayIndexedExpression.position), args, false, arrayIndexedExpression.position)
                    return listOf(AstReplaceNode(assignment, poke, assignment.parent))
                } else {
                    val peek = FunctionCallExpression(IdentifierReference(listOf("peekbool"), arrayIndexedExpression.position), mutableListOf(address), arrayIndexedExpression.position)
                    return listOf(AstReplaceNode(arrayIndexedExpression, peek, parent))
                }
            } else if(arrayVar.datatype.sub==BaseDataType.LONG) {
                // use peekl/pokel
                if(parent is AssignTarget) {
                    val assignment = parent.parent as Assignment
                    val args = mutableListOf(address, assignment.value)
                    val poke = FunctionCallStatement(IdentifierReference(listOf("pokel"), arrayIndexedExpression.position), args, false, arrayIndexedExpression.position)
                    return listOf(AstReplaceNode(assignment, poke, assignment.parent))
                } else {
                    val peek = FunctionCallExpression(IdentifierReference(listOf("peekl"), arrayIndexedExpression.position), mutableListOf(address), arrayIndexedExpression.position)
                    return listOf(AstReplaceNode(arrayIndexedExpression, peek, parent))
                }
            } else if(arrayVar.datatype.sub==BaseDataType.FLOAT) {
                // use peekf/pokef
                if(parent is AssignTarget) {
                    val assignment = parent.parent as Assignment
                    val args = mutableListOf(address, assignment.value)
                    val poke = FunctionCallStatement(IdentifierReference(listOf("pokef"), arrayIndexedExpression.position), args, false, arrayIndexedExpression.position)
                    return listOf(AstReplaceNode(assignment, poke, assignment.parent))
                } else {
                    val peek = FunctionCallExpression(IdentifierReference(listOf("peekf"), arrayIndexedExpression.position), mutableListOf(address), arrayIndexedExpression.position)
                    return listOf(AstReplaceNode(arrayIndexedExpression, peek, parent))
                }
            }
        } else if(arrayVar!=null && (arrayVar.type==VarDeclType.MEMORY || arrayVar.datatype.isString || arrayVar.datatype.isPointer || arrayVar.datatype.isArray)) {
            return noModifications
        }
//        else if(arrayVar!=null) {
//            // it could be a pointer dereference instead of a simple array variable
//            TODO("deref[word] rewrite ????  ${arrayIndexedExpression.position}")
//            val dt = arrayIndexedExpression.plainarrayvar!!.traverseDerefChainForDt(null)
//            if(dt.isUnsignedWord) {
//                // ptr.field[index] -->  @(ptr.field + index)
//                val index = arrayIndexedExpression.indexer.indexExpr
//                val address = BinaryExpression(arrayIndexedExpression.arrayvar.copy(), "+", index, arrayIndexedExpression.position)
//                if(parent is AssignTarget) {
//                    val memwrite = DirectMemoryWrite(address, arrayIndexedExpression.position)
//                    return listOf(AstReplaceNode(arrayIndexedExpression, memwrite, parent))
//                } else {
//                    val memread = DirectMemoryRead(address, arrayIndexedExpression.position)
//                    return listOf(AstReplaceNode(arrayIndexedExpression, memread, parent))
//                }
//            }
//        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<AstModification> {
        fun isStringComparison(leftDt: InferredTypes.InferredType, rightDt: InferredTypes.InferredType): Boolean {
            return when {
                leftDt issimpletype BaseDataType.STR && rightDt issimpletype BaseDataType.STR -> true
                leftDt issimpletype BaseDataType.UWORD && rightDt issimpletype BaseDataType.STR || leftDt issimpletype BaseDataType.STR && rightDt issimpletype BaseDataType.UWORD -> true
                leftDt issimpletype BaseDataType.LONG && rightDt issimpletype BaseDataType.STR || leftDt issimpletype BaseDataType.STR && rightDt issimpletype BaseDataType.LONG -> target.POINTER_MEM_SIZE > 2u
                leftDt.isPointer && leftDt.getOrUndef().sub == BaseDataType.UBYTE -> rightDt issimpletype BaseDataType.STR
                rightDt.isPointer && rightDt.getOrUndef().sub == BaseDataType.UBYTE -> leftDt issimpletype BaseDataType.STR
                else -> false
            }
        }

        if(expr.operator=="in") {
            val containment = ContainmentCheck(expr.left, expr.right, expr.position)
            return listOf(AstReplaceNode(expr, containment, parent))
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
                return listOf(AstReplaceNode(expr, comparison, parent))
            }
        }

        if(expr.operator=="*" && expr.inferType(program).isInteger && expr.left isSameAs expr.right) {
            // replace squaring with call to builtin function to do this in a more optimized way
            val leftDt = expr.left.inferType(program)
            val function = if(leftDt.isBytes) "prog8_lib_square_byte" else if(leftDt.isWords) "prog8_lib_square_word" else "prog8_lib_square_long"
            val squareCall = FunctionCallExpression(
                IdentifierReference(listOf(function), expr.position),
                mutableListOf(expr.left.copy()), expr.position)
            return listOf(AstReplaceNode(expr, squareCall, parent))
        }

        if(expr.operator==".") {
            if(expr.left is IdentifierReference) {
                if(expr.right is IdentifierReference) {
                    // (a.b).c -> a.b.c  - merge dotted names (handles parenthesized pointer chains like (t.next).flag)
                    val leftIdent = expr.left as IdentifierReference
                    val rightIdent = expr.right as IdentifierReference
                    val combined = IdentifierReference(leftIdent.nameInSource + rightIdent.nameInSource, expr.position)
                    return listOf(AstReplaceNode(expr, combined, parent))
                }
                val ri = expr.right as? ArrayIndexedExpression
                if(ri!=null && ri.plainarrayvar!=null) {
                    val leftIdent = expr.left as IdentifierReference
                    val leftVar = leftIdent.targetVarDecl()
                    if(leftVar != null && leftVar.datatype.isPointer && leftVar.datatype.subType != null) {
                        // ptr.field[idx] -> convert to (^^field)[idx] for struct field array access
                        val fieldName = ri.plainarrayvar!!.nameInSource
                        val chain = leftIdent.nameInSource + fieldName
                        val ptrDeref = PtrDereference(chain, false, expr.position)
                        val ai = ArrayIndexedExpression(null, null, ptrDeref, ri.indexer, expr.position)
                        return listOf(AstReplaceNode(expr, ai, parent))
                    }
                    // a.b   .  c.d[i]  ->  a.b.c.d[i]
                    val joined = leftIdent.nameInSource + ri.plainarrayvar!!.nameInSource
                    val ai = ArrayIndexedExpression(IdentifierReference(joined, expr.position), null, null, ri.indexer, expr.position)
                    return listOf(AstReplaceNode(expr, ai, parent))
                }
            }


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
                        val newleft = ArrayIndexedExpression(combinedIdentifier, null, null, left.indexer, left.position)
                        val newright = IdentifierReference(listOf(right.chain.single()), right.position)
                        return listOf(
                            AstReplaceNode(parent.left, newleft, parent),
                            AstReplaceNode(parent.right, newright, parent)
                        )
                    }
                }
            }

            if(expr.left is ArrayIndexedExpression && right!=null) {
                // replace  replace x.y.listarray[2]^^.value    with  just  x.y.listarray[2] . value
                // this will be further modified elsewhere
                val ident = IdentifierReference(right.chain, right.position)
                return listOf(AstReplaceNode(expr.right, ident, expr))
            }

            // Generic pointer field access on complex expressions, e.g. (expr as ^^Struct).field
            // Simple identifier/array left-hand-sides (a.b.c, ptr[i].field) are handled elsewhere.
            // Writes through such an expression can't occur: the grammar only allows them as read expressions.
            if(expr.left !is IdentifierReference && expr.left !is ArrayIndexedExpression) {
                val fieldIdent = expr.right as? IdentifierReference
                if(fieldIdent!=null) {
                    val leftDt = expr.left.inferType(program).getOrUndef()
                    val struct = if(leftDt.isPointer) leftDt.subType as? StructDecl else null
                    val fieldName = fieldIdent.nameInSource.first()
                    val fieldDt = struct?.getFieldType(fieldName)
                    if(struct!=null && fieldDt!=null) {
                        // (ptr).field  -->  peekXXX(ptr as uword/long + offsetof(Struct.field))
                        val offset = struct.offsetof(fieldName, program.target)!!.toInt()
                        // cast to an integer type of the target's pointer size: keeps the '+' unscaled
                        // if ptr is already a typecast (e.g. t.port as ^^Thing), unwrap to avoid
                        // a WORD->POINTER->LONG chain that confuses the IR register allocator on 32-bit targets
                        val ptrSrc = (expr.left as? TypecastExpression)?.expression ?: expr.left
                        val ptrAsInt = TypecastExpression(ptrSrc.copy(), target.pointerType, true, ptrSrc.position)
                        val address: Expression = if(offset==0) ptrAsInt
                            else BinaryExpression(ptrAsInt, "+", NumericLiteral.optimalInteger(offset, expr.position), expr.position)
                        fun peekCall(func: String) =
                            FunctionCallExpression(IdentifierReference(listOf(func), expr.position), mutableListOf(address), expr.position)
                        val readExpr: Expression = when {
                            fieldDt.isBool -> peekCall("peekbool")
                            fieldDt.isUnsignedByte -> DirectMemoryRead(address, expr.position)
                            fieldDt.isSignedByte -> TypecastExpression(DirectMemoryRead(address, expr.position), DataType.BYTE, true, expr.position)
                            fieldDt.isUnsignedWord -> peekCall("peekw")
                            fieldDt.isSignedWord -> TypecastExpression(peekCall("peekw"), DataType.WORD, true, expr.position)
                            fieldDt.isLong -> peekCall("peekl")
                            fieldDt.isFloat -> peekCall("peekf")
                            fieldDt.isPointer -> {
                                // pointer fields must be read with the target's pointer size
                                val peekName = if(target.POINTER_MEM_SIZE > 2u) "peekl" else "peekw"
                                TypecastExpression(peekCall(peekName), fieldDt, true, expr.position)
                            }
                            else -> {
                                errors.err("unsupported field type for pointer dereference", expr.position)
                                return noModifications
                            }
                        }
                        if(fieldIdent.nameInSource.size==1)
                            return listOf(AstReplaceNode(expr, readExpr, parent))
                        // (ptr).field.rest...  -->  (read of first field) . rest...   (will be desugared further in the next pass)
                        if(!fieldDt.isPointer) {
                            errors.err("cannot dereference non-pointer field '$fieldName'", fieldIdent.position)
                            return noModifications
                        }
                        val rest = IdentifierReference(fieldIdent.nameInSource.drop(1), fieldIdent.position)
                        val chained = BinaryExpression(readExpr, ".", rest, expr.position)
                        return listOf(AstReplaceNode(expr, chained, parent))
                    }
                }
            }
        }

        return noModifications
    }

    override fun after(memread: DirectMemoryRead, parent: Node): Iterable<AstModification> {
        // for word variables:
        // @(&var) --> lsb(var)
        // @(&var+1) --> msb(var)           NOTE: ONLY WHEN VAR IS AN ACTUAL WORD VARIABLE (POINTER)

        val addrOf = memread.addressExpression as? AddressOf
        if(addrOf?.arrayIndex!=null)
            return noModifications
        if(addrOf!=null && addrOf.identifier?.inferType(program)?.isWords==true) {
            val lsb = FunctionCallExpression(IdentifierReference(listOf("lsb"), memread.position), mutableListOf(addrOf.identifier!!), memread.position)
            return listOf(AstReplaceNode(memread, lsb, parent))
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
                    return listOf(AstReplaceNode(memread, msb, parent))
                }
            }
        }

        return noModifications
    }

    override fun after(chainedAssignment: ChainedAssignment, parent: Node): Iterable<AstModification> {
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
            return listOf(AstReplaceNode(lastChained,
                AnonymousScope(assigns, chainedAssignment.position), lastChained.parent))
        }
        return noModifications
    }

    override fun after(whenChoice: WhenChoice, parent: Node): Iterable<AstModification> {
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
                        val cast = num.cast(dt, true, target)
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

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<AstModification> {

        if (identifier.nameInSource.size>1) {
            val firstTarget = (identifier.firstTarget() as? VarDecl)
            val firstDt = firstTarget?.datatype
            if (firstDt?.isPointer == true) {
                // the a.b.c.d can be a pointer dereference chain ending in a struct field;  a^^.b^^.c^^.d
                val chain = mutableListOf(identifier.nameInSource[0])
                var struct = firstDt.subType
                for(name in identifier.nameInSource.drop(1)) {
                    if(struct==null) {
                        errors.err("cannot lookup fields through untyped pointers", position = identifier.position)
                        return noModifications
                    }
                    val fieldDt = struct.getFieldType(name)
                    if(fieldDt==null) {
                        errors.err("unknown field '${name}' in struct '${struct.scopedNameString}'", identifier.position)
                        return noModifications
                    }
                    if(fieldDt.isPointer) {
                        chain.add(name)
                        struct = fieldDt.subType
                    } else {
                        chain.add(name)
                        struct = null
                    }
                }
                val deref = PtrDereference(chain, false, identifier.position)
                return listOf(AstReplaceNode(identifier, deref, parent))
            }
        }
        return noModifications
    }

    override fun after(ongoto: OnGoto, parent: Node): Iterable<AstModification> {
        val indexDt = ongoto.index.inferType(program).getOrUndef()
        if(!indexDt.isUnsignedByte)
            return noModifications

        val numlabels = ongoto.labels.size
        val elementDt = program.target.pointerBaseType
        val arrayDt = DataType.arrayFor(elementDt, program.target)
        val labelArray = ArrayLiteral(InferredTypes.knownFor(arrayDt), ongoto.labels.toTypedArray(), ongoto.position)
        val jumplistArray = VarDecl.createAutoOptionalSplit(labelArray, target)

        val indexValue: Expression
        val conditionVar: VarDecl?
        val assignIndex: Assignment?

        // put condition in temp var, if it is not simple; to avoid evaluating expression multiple times
        if (ongoto.index.isSimple) {
            indexValue = ongoto.index
            assignIndex = null
            conditionVar = null
        } else {
            conditionVar = VarDecl.createAuto(indexDt, ongoto.index.position)
            indexValue = IdentifierReference(listOf(conditionVar.name), conditionVar.position)
            val varTarget = AssignTarget(indexValue, null, null, null, false, position=conditionVar.position)
            assignIndex = Assignment(varTarget, ongoto.index, AssignmentOrigin.USERCODE, ongoto.position)
        }

        val callTarget = ArrayIndexedExpression(IdentifierReference(listOf(jumplistArray.name), jumplistArray.position), null, null, ArrayIndex(indexValue.copy(), indexValue.position), ongoto.position)
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
                mutableListOf(ifSt)
            else
                mutableListOf(conditionVar, assignIndex!!, ifSt)
            , ongoto.position)
        return listOf(
            AstReplaceNode(ongoto, replacementScope, parent),
            AstInsert.first(ongoto.definingScope, jumplistArray)
        )
    }

    override fun after(deref: PtrDereference, parent: Node): Iterable<AstModification> {
        val isLHS = parent is AssignTarget
        val varDt = (deref.firstTarget() as? VarDecl)?.datatype
        val isRawAddressHolder = varDt?.isUnsignedWord==true || (target.POINTER_MEM_SIZE > 2u && varDt?.isLong==true)
        if(isRawAddressHolder || (varDt?.isPointer==true && varDt.sub?.isByte==true)) {
            // replace  ptr^^   by  @(ptr)    when ptr is raw address holder or ^^byte
            val identifier = IdentifierReference(deref.chain, deref.position)
            if(isLHS && varDt.sub==BaseDataType.UBYTE) {
                val memwrite = DirectMemoryWrite(identifier, deref.position)
                return listOf(AstReplaceNode(deref, memwrite, parent))
            } else if(!isLHS) {
                val memread = DirectMemoryRead(identifier, deref.position)
                val replacement = if (varDt.sub == BaseDataType.BYTE)
                    TypecastExpression(memread, DataType.BYTE, true, memread.position)
                else
                    memread
                return listOf(AstReplaceNode(deref, replacement, parent))
            }
        }

        val expr = deref.parent as? BinaryExpression
        if (expr != null && expr.operator == ".") {
            if (expr.left is IdentifierReference && expr.right === deref) {
                // replace  (a) . (b^^)  by (a.b)^^
                val name = (expr.left as IdentifierReference).nameInSource + deref.chain
                val replacement = PtrDereference(name, deref.derefLast, deref.position)
                return listOf(AstReplaceNode(expr, replacement, expr.parent))
            } else if(expr.left===deref && expr.right is ArrayIndexedExpression) {
                // replace  (a^^) . ( s[b] )  by  (a^^.s^^)[b]
                val idx = expr.right as ArrayIndexedExpression
                if(idx.plainarrayvar!=null) {
                    val name = deref.chain + idx.plainarrayvar!!.nameInSource
                    val ptrDeref = PtrDereference(name, false, deref.position)
                    val indexer = ArrayIndexedExpression(null, null, ptrDeref, idx.indexer, idx.position)
                    return listOf(AstReplaceNode(expr, indexer, expr.parent))
                } else {
                    TODO("convert ptr.p[idx]  ${idx.position}")
                }
            }
        }

        return noModifications
    }

    override fun after(forLoop: ForLoop, parent: Node): Iterable<AstModification> {
        // Desugar list iteration and reverse array/string iteration (ideas/list-iteration.md Option A)
        // - for x in list [step -1]  -> while cursor.Succ/Pred !=0 with next/prev saved
        // - for x in array step -1  -> index loop len-1 downto 0
        // - for c in string step -1 -> similar via index
        val iterableDt = forLoop.iterable.inferType(program).getOrUndef()
        val isList = isListIterable(iterableDt)
        val isArray = iterableDt.isArray
        val isString = iterableDt.isString
        val stepVal = forLoop.step?.constValue(program)?.number
        val isReverse = stepVal == -1.0
        val isForward = forLoop.step==null || stepVal==1.0
        if(!isList && !(isArray || isString)) return noModifications
        if(isList) {
            // only 1 / -1 already validated in AstChecker
            if(!isForward && !isReverse) return noModifications
            return desugarListForLoop(forLoop, parent, isReverse)
        }
        if((isArray || isString) && isReverse) {
            // Keep this in desugaring: backend support would duplicate descending
            // index handling for every array/string kind in every code generator,
            // for a relatively uncommon use case.
            return desugarReverseArrayStringForLoop(forLoop, parent, iterableDt)
        }
        // forward array/string with step 1 is handled by normal codegen, no desugaring
        return noModifications
    }

    private fun isListIterable(dt: DataType): Boolean = ListIterationHelper.isListIterable(dt, program)
    private fun structDeclFor(dt: DataType): StructDecl? = ListIterationHelper.structDeclFor(dt, program)
    private fun desugarListForLoop(forLoop: ForLoop, parent: Node, reverse: Boolean): Iterable<AstModification> {
        val pos = forLoop.position
        // need list struct to know Head/TailPred field names and node link names
        val iterableDt = forLoop.iterable.inferType(program).getOrUndef()
        val listDecl = structDeclFor(iterableDt) ?: return noModifications
        val nodeDecl = structDeclFor(listDecl.fields[0].type) ?: return noModifications
        val linkSuccName = nodeDecl.fields[0].name // Succ or Next
        val linkPredName = nodeDecl.fields[1].name // Pred or Prev
        val linkName = if(reverse) linkPredName else linkSuccName
        val headField = if(reverse) "TailPred" else "Head"
        // Instead create a synthetic pointer variable in the enclosing scope - typed to the list's node
        // For simplicity, create a new VarDecl for cursor in the same block as the for loop
        val cursorDt = DataType.pointer(nodeDecl)
        val loopVarName = forLoop.loopVar.nameInSource.singleOrNull() ?: return noModifications
        // ensure loop var exists (implicit var may not have been created if AstChecker/Implicit pass missed it)
        val existingLoopVar = forLoop.loopVar.targetVarDecl()
        val loopVarDecl: VarDecl? = if(existingLoopVar==null) {
            val effType = forLoop.loopVarType ?: DataType.pointer(nodeDecl)
            VarDecl.builder(effType, pos).names(loopVarName).type(VarDeclType.VAR).build()
        } else null
        // make cursor/next names unique per for-loop position to avoid hoisting collisions in the same subroutine
        val uniq = "${pos.line}_${pos.startCol}"
        val cursorVar = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, cursorDt, ZeropageWish.DONTCARE, SplitWish.DONTCARE, null, null, "list_cursor_${loopVarName}_${uniq}", emptyList(), null, false, 0u, false, null, pos)
        // Build: cursor = iterable.Head (or TailPred) - use IdentifierReference with dotted name for struct field
        val iterableRefCopy = forLoop.iterable.copy()
        val iterableName = (iterableRefCopy as? IdentifierReference)?.nameInSource ?: return noModifications
        val headAccess: Expression = IdentifierReference(iterableName + headField, pos)
        val cursorAssign = Assignment(AssignTarget(IdentifierReference(listOf(cursorVar.name), pos), null, null, null, false, position=pos), headAccess, AssignmentOrigin.OPTIMIZER, pos)
        // Stop at the embedded list sentinel rather than relying on a null link.
        val sentinel: Expression = if(reverse) {
            // AddressOf a pointer field is represented as the target's raw address.
            AddressOf(IdentifierReference(iterableName + "Head", pos), null, null, false, false, pos)
        } else {
            AddressOf(IdentifierReference(iterableName + "Tail", pos), null, null, false, false, pos)
        }
        val condition = BinaryExpression(IdentifierReference(listOf(cursorVar.name), pos), "!=", sentinel, pos)
        // use IdentifierReference with dotted access; later desugarer pass will convert pointer field access to PtrDereference
        val linkFieldAccessForNext = IdentifierReference(listOf(cursorVar.name, linkName), pos)
        // next/prev temp - also typed to node pointer for dereference
        val nextDt = DataType.pointer(nodeDecl)
        val nextVar = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, nextDt, ZeropageWish.DONTCARE, SplitWish.DONTCARE, null, null, "list_next_${loopVarName}_${uniq}", emptyList(), null, false, 0u, false, null, pos)
        val nextAssign = Assignment(AssignTarget(IdentifierReference(listOf(nextVar.name), pos), null, null, null, false, position=pos), linkFieldAccessForNext, AssignmentOrigin.OPTIMIZER, pos)
        val loopVarAssign = Assignment(forLoop.loopVar.let { AssignTarget(it.copy(), null, null, null, false, position=pos) }, IdentifierReference(listOf(cursorVar.name), pos), AssignmentOrigin.OPTIMIZER, pos)
        val cursorUpdate = Assignment(AssignTarget(IdentifierReference(listOf(cursorVar.name), pos), null, null, null, false, position=pos), IdentifierReference(listOf(nextVar.name), pos), AssignmentOrigin.OPTIMIZER, pos)
        val whileBody = AnonymousScope(mutableListOf<Statement>(nextAssign, loopVarAssign).apply { addAll(forLoop.body.statements) }.apply { add(cursorUpdate) }, pos)
        val whileLoop = WhileLoop(condition, whileBody, pos)
        val stmts = mutableListOf<Statement>()
        if(loopVarDecl!=null) stmts.add(loopVarDecl)
        stmts.add(cursorVar)
        stmts.add(nextVar)
        stmts.add(cursorAssign)
        stmts.add(whileLoop)
        val replacement = AnonymousScope(stmts, pos)
        // set parents
        replacement.linkParents(parent)
        return listOf(AstReplaceNode(forLoop, replacement, parent))
    }
    private fun desugarReverseArrayStringForLoop(forLoop: ForLoop, parent: Node, iterableDt: DataType): Iterable<AstModification> {
        val pos = forLoop.position
        // Create index variable - make unique per loop position to avoid hoisting collisions
        val uniqRev = "${pos.line}_${pos.startCol}"
        val idxName = "rev_idx_${forLoop.loopVar.nameInSource.single()}_${uniqRev}"
        // Determine length: for array, use arraysize; for string, need runtime len? For now use while to find len for string via scanning (simplified: use 255 max? Better to use len via function? For now handle array only; for string fallback to not desugar and let IR handle? To keep simple, only handle array with known size; for string we generate scanning loop.
        // If array with known size, init idx = len-1
        val syntheticIterableName = "rev_str_${forLoop.loopVar.nameInSource.single()}_${uniqRev}"
        val syntheticIterable: VarDecl?
        val iterableRef: IdentifierReference
        val arrayVar: VarDecl?
        val originalIterable = forLoop.iterable
        if(originalIterable is IdentifierReference) {
            iterableRef = originalIterable.copy()
            arrayVar = originalIterable.targetVarDecl()
            syntheticIterable = null
        } else if(iterableDt.isString && forLoop.iterable is StringLiteral) {
            iterableRef = IdentifierReference(listOf(syntheticIterableName), pos)
            arrayVar = null
            syntheticIterable = VarDecl(
                VarDeclType.VAR,
                VarDeclOrigin.STRINGLITERAL,
                DataType.STR,
                ZeropageWish.DONTCARE,
                SplitWish.DONTCARE,
                null,
                null,
                syntheticIterableName,
                emptyList(),
                forLoop.iterable.copy() as StringLiteral,
                false,
                0u,
                false,
                null,
                pos
            )
        } else {
            return noModifications
        }
        if(iterableDt.isArray) {
            if(arrayVar==null) return noModifications
            val arrSize = arrayVar.arraysize?.indexExpr?.constValue(program)?.number?.toInt() ?: return noModifications
            if(arrSize<=0) return noModifications
            // Use byte idx when array small enough to avoid "array indexing is limited to byte size" on 6502 targets
            val idxType = if (arrSize <= 255) DataType.UBYTE else DataType.UWORD
            val wordIdxVar = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, idxType, ZeropageWish.DONTCARE, SplitWish.DONTCARE, null, null, idxName, emptyList(), null, false, 0u, false, null, pos)
            val wordIdxInit = Assignment(AssignTarget(IdentifierReference(listOf(idxName), pos), null, null, null, false, position=pos), NumericLiteral.optimalInteger(arrSize-1, pos), AssignmentOrigin.OPTIMIZER, pos)
            val condition = BinaryExpression(IdentifierReference(listOf(idxName), pos), "<", NumericLiteral.optimalInteger(arrSize, pos), pos)
            val elementAssign = Assignment(forLoop.loopVar.let { AssignTarget(it.copy(), null, null, null, false, position=pos) }, ArrayIndexedExpression(iterableRef.copy(), null, null, ArrayIndex(IdentifierReference(listOf(idxName), pos), pos), pos), AssignmentOrigin.OPTIMIZER, pos)
            val idxDec = Assignment(AssignTarget(IdentifierReference(listOf(idxName), pos), null, null, null, false, position=pos), BinaryExpression(IdentifierReference(listOf(idxName), pos), "-", NumericLiteral.optimalInteger(1, pos), pos), AssignmentOrigin.OPTIMIZER, pos)
            val whileBody = AnonymousScope(mutableListOf<Statement>(elementAssign).apply { addAll(forLoop.body.statements) }.apply { add(idxDec) }, pos)
            val whileLoop = WhileLoop(condition, whileBody, pos)
            val replacement = AnonymousScope(mutableListOf<Statement>(wordIdxVar, wordIdxInit, whileLoop), pos)
            replacement.linkParents(parent)
            return listOf(AstReplaceNode(forLoop, replacement, parent))
        } else if(iterableDt.isString) {
            // string reverse: need to compute len via scanning? Use while to find terminator length
            // For now generate similar index loop but compute len first:
            // word len=0; while s[len]!=0 { len++ }; len-- ; while len>=0 { c=s[len]; body; len-- }
            val lenName = "str_len_${forLoop.loopVar.nameInSource.single()}_${uniqRev}"
            val strIdxType = if (target.cpu.is6502) DataType.UBYTE else DataType.UWORD
            val lenVar = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, strIdxType, ZeropageWish.DONTCARE, SplitWish.DONTCARE, null, null, lenName, emptyList(), null, false, 0u, false, null, pos)
            val idxVarWord = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, strIdxType, ZeropageWish.DONTCARE, SplitWish.DONTCARE, null, null, idxName, emptyList(), null, false, 0u, false, null, pos)
            val lenInit = Assignment(AssignTarget(IdentifierReference(listOf(lenName), pos), null, null, null, false, position=pos), NumericLiteral.optimalInteger(0, pos), AssignmentOrigin.OPTIMIZER, pos)
            val lenCond = BinaryExpression(ArrayIndexedExpression(iterableRef.copy(), null, null, ArrayIndex(IdentifierReference(listOf(lenName), pos), pos), pos), "!=", NumericLiteral.optimalInteger(0, pos), pos)
            val lenInc = Assignment(AssignTarget(IdentifierReference(listOf(lenName), pos), null, null, null, false, position=pos), BinaryExpression(IdentifierReference(listOf(lenName), pos), "+", NumericLiteral.optimalInteger(1, pos), pos), AssignmentOrigin.OPTIMIZER, pos)
            val lenLoop = WhileLoop(lenCond, AnonymousScope(mutableListOf<Statement>(lenInc), pos), pos)
            val idxInit = Assignment(AssignTarget(IdentifierReference(listOf(idxName), pos), null, null, null, false, position=pos), BinaryExpression(IdentifierReference(listOf(lenName), pos), "-", NumericLiteral.optimalInteger(1, pos), pos), AssignmentOrigin.OPTIMIZER, pos)
            // unsigned wrap condition: idx < len (start len-1, wraps to 65535 after 0 and exits)
            val cond = BinaryExpression(IdentifierReference(listOf(idxName), pos), "<", IdentifierReference(listOf(lenName), pos), pos)
            val elementAssign = Assignment(forLoop.loopVar.let { AssignTarget(it.copy(), null, null, null, false, position=pos) }, ArrayIndexedExpression(iterableRef.copy(), null, null, ArrayIndex(IdentifierReference(listOf(idxName), pos), pos), pos), AssignmentOrigin.OPTIMIZER, pos)
            val idxDec = Assignment(AssignTarget(IdentifierReference(listOf(idxName), pos), null, null, null, false, position=pos), BinaryExpression(IdentifierReference(listOf(idxName), pos), "-", NumericLiteral.optimalInteger(1, pos), pos), AssignmentOrigin.OPTIMIZER, pos)
            val whileBody = AnonymousScope(mutableListOf<Statement>(elementAssign).apply { addAll(forLoop.body.statements) }.apply { add(idxDec) }, pos)
            val whileLoop = WhileLoop(cond, whileBody, pos)
            val replacementStatements = mutableListOf<Statement>()
            if(syntheticIterable!=null) replacementStatements.add(syntheticIterable)
            replacementStatements.addAll(listOf(lenVar, idxVarWord, lenInit, lenLoop, idxInit, whileLoop))
            val replacement = AnonymousScope(replacementStatements, pos)
            replacement.linkParents(parent)
            return listOf(AstReplaceNode(forLoop, replacement, parent))
        }
        return noModifications
    }

    override fun after(deref: ArrayIndexedPtrDereference, parent: Node): Iterable<AstModification> {
        // get rid of the ArrayIndexedPtrDereference AST node, replace it with other AST nodes that are equivalent

        /**
         * Build the chain from a BinaryExpression with "." operator (e.g., "ptr[ idx].field").
         * Returns null if the expression doesn't match this pattern.
         */
        fun buildChainFromDotExpression(expr: BinaryExpression): List<Pair<String, ArrayIndex?>>? {
            if(expr.operator!=".") return null
            val right = expr.right as? IdentifierReference ?: return null
            val left = expr.left
            // left could be ArrayIndexedExpression (for ptr[idx]) or IdentifierReference (for ptr)
            return when(left) {
                is ArrayIndexedExpression -> {
                    val ptrName = left.plainarrayvar?.nameInSource ?: return null
                    listOf(Pair(ptrName[0], left.indexer), Pair(right.nameInSource[0], null))
                }
                is IdentifierReference -> {
                    val ptrName = left.nameInSource
                    listOf(Pair(ptrName[0], null), Pair(right.nameInSource[0], null))
                }
                is BinaryExpression -> {
                    // Nested dot expression like a.b.c
                    val leftChain = buildChainFromDotExpression(left) ?: return null
                    leftChain + Pair(right.nameInSource[0], null)
                }
                else -> null
            }
        }

        /**
         * For augmented assignments, convert pointer dereferences in the value that match the same memory location
         * to DirectMemoryRead, so SimplifiedAstMaker can recognize the augmented pattern.
         */
        fun convertAugmentedValueToMemoryRead(value: Expression, origDeref: ArrayIndexedPtrDereference, address: Expression): Expression {
            // Check if the value IS the same pointer dereference - replace directly
            if(value is ArrayIndexedPtrDereference && value.chain == origDeref.chain && value.derefLast == origDeref.derefLast) {
                return DirectMemoryRead(address.copy(), value.position)
            }
            // Handle "ptr[idx].field" represented as BinaryExpression with "." operator
            if(value is BinaryExpression && value.operator==".") {
                val chain = buildChainFromDotExpression(value)
                if(chain != null && chain == origDeref.chain && origDeref.derefLast == false) {
                    return DirectMemoryRead(address.copy(), value.position)
                }
            }
            // For BinaryExpressions, recursively check left and right
            if(value is BinaryExpression) {
                val newLeft = convertAugmentedValueToMemoryRead(value.left, origDeref, address)
                val newRight = convertAugmentedValueToMemoryRead(value.right, origDeref, address)
                if(newLeft !== value.left || newRight !== value.right) {
                    // Use replaceChildNode to properly update parent links
                    val result = value.copy()
                    if(newLeft !== value.left) {
                        result.replaceChildNode(result.left, newLeft)
                    }
                    if(newRight !== value.right) {
                        result.replaceChildNode(result.right, newRight)
                    }
                    return result
                }
            }
            // For PrefixExpressions, recursively check the inner expression
            if(value is PrefixExpression) {
                val newInner = convertAugmentedValueToMemoryRead(value.expression, origDeref, address)
                if(newInner !== value.expression) {
                    val result = value.copy()
                    result.replaceChildNode(result.expression, newInner)
                    return result
                }
            }
            return value
        }

        fun pokeFunc(dt: DataType): Pair<String, DataType?> {
            return when {
                dt.isBool -> "pokebool" to null
                dt.isUnsignedByte -> "poke" to null
                dt.isSignedByte -> "poke" to DataType.UBYTE
                dt.isUnsignedWord -> "pokew" to null
                dt.isSignedWord -> "pokew" to DataType.UWORD
                dt.isLong -> "pokel" to null
                dt.isFloat -> "pokef" to null
                else -> throw FatalAstException("can only deref a numeric or boolean pointer here")
            }
        }

        if(parent is AssignTarget) {
            if(!deref.derefLast) {
                val assignment = parent.parent as Assignment
                val field = deref.chain.last()
                val ptr = deref.chain.dropLast(1)
                if(field.second==null && ptr.last().second!=null) {
                    val ptrName = ptr.map { it.first }
                    val ptrVar = deref.definingScope.lookup(ptrName) as? VarDecl
                    if(ptrVar!=null && (ptrVar.datatype.isPointer || ptrVar.datatype.isPointerArray)) {
                        val struct = ptrVar.datatype.subType!! as StructDecl
                        val offsetNumber = NumericLiteral.optimalInteger(struct.offsetof(field.first, program.target)!!.toInt(), deref.position)
                        val pointerIdentifier = IdentifierReference(ptrName, deref.position)
                        val addrType = target.pointerType
                        val address: Expression
                        if(ptrVar.datatype.isPointer) {
                            // pointer[idx].field = value       -->  pokeXXX(pointer as uword/long + idx*sizeof(Struct) + offsetof(Struct.field), value)
                            val structSize = ptrVar.datatype.dereference().size(program.target)
                            val pointerAsAddr = TypecastExpression(pointerIdentifier, addrType, true, deref.position)
                            val idx = ptr.last().second!!.indexExpr
                            val scaledIndex = BinaryExpression(idx, "*", NumericLiteral(BaseDataType.UWORD, structSize.toDouble(), deref.position), deref.position)
                            val structAddr = BinaryExpression(pointerAsAddr, "+", scaledIndex, deref.position)
                            address = BinaryExpression(structAddr, "+", offsetNumber, deref.position)
                        }
                        else {
                            // pointerarray[idx].field = value  -->  pokeXXX(pointerarray[idx] as uword/long + offsetof(Struct.field), value)
                            val index = ArrayIndexedExpression(pointerIdentifier, null, null, ptr.last().second!!, deref.position)
                            val pointerAsAddr = TypecastExpression(index, addrType, true, deref.position)
                            address = BinaryExpression(pointerAsAddr, "+", offsetNumber, deref.position)
                        }

                        // For augmented assignments, keep as DirectMemoryWrite so the IR codegen can optimize in-place.
                        // Also convert matching pointer dereferences in the value to DirectMemoryRead for proper recognition.
                        // Check for augmented pattern: value references the same memory location as the address.

                        /**
                         * Check if a value expression represents an augmented assignment pattern for a memory target.
                         * E.g., @(addr) = @(addr) + 1  or  @(addr) = ~@(addr)
                         */
                        fun isAugmentedMemoryPattern(value: Expression, addr: Expression, origDeref: ArrayIndexedPtrDereference): Boolean {
                            fun Expression.referencesSameAddress(a: Expression, od: ArrayIndexedPtrDereference): Boolean {
                                if(this is DirectMemoryRead)
                                    return this.addressExpression isSameAs a
                                if(this is ArrayIndexedPtrDereference)
                                    return this.chain == od.chain && this.derefLast == od.derefLast
                                // Handle "ptr[idx].field" represented as BinaryExpression with "." operator
                                if(this is BinaryExpression && this.operator==".") {
                                    // Check if this corresponds to the same chain as origDeref
                                    // Build the chain from the binary expression and compare
                                    val chain = buildChainFromDotExpression(this)
                                    return chain != null && chain == od.chain
                                }
                                return false
                            }
                            if(value is BinaryExpression) {
                                if(value.left.referencesSameAddress(addr, origDeref)) return true
                                if(value.operator in CommutativeOperators && value.right.referencesSameAddress(addr, origDeref)) return true
                                if(value.operator in "+-" && value.right is BinaryExpression) {
                                    val rightBin = value.right as BinaryExpression
                                    if(rightBin.left.referencesSameAddress(addr, origDeref) || rightBin.right.referencesSameAddress(addr, origDeref)) return true
                                }
                            }
                            if(value is PrefixExpression) {
                                return value.expression.referencesSameAddress(addr, origDeref)
                            }
                            return false
                        }

                        val isAugmentedPattern = isAugmentedMemoryPattern(assignment.value, address, deref)
                        if(isAugmentedPattern) {
                            val memwrite = DirectMemoryWrite(address, deref.position)
                            val target = AssignTarget(null, null, memwrite, null, false, position = deref.position)
                            val newValue = convertAugmentedValueToMemoryRead(assignment.value, deref, address)
                            val newAssignment = Assignment(target, newValue, assignment.origin, assignment.position)
                            newAssignment.isAugmentedMemoryAssign = true
                            return listOf(AstReplaceNode(assignment, newAssignment, assignment.parent))
                        }

                        val (pokeFunc, valueCast) = pokeFunc(parent.inferType(program).getOrUndef())
                        val value = if(valueCast==null) assignment.value else TypecastExpression(assignment.value, valueCast, true, assignment.value.position)
                        val pokeCall = FunctionCallStatement(IdentifierReference(listOf(pokeFunc), assignment.position),
                            mutableListOf(address, value), false, assignment.position)
                        return listOf(AstReplaceNode(assignment, pokeCall, assignment.parent))
                    }
                }
            }
        }


        if(deref.chain.last().second!=null && deref.derefLast && deref.chain.dropLast(1).all { it.second==null } ) {

            // parent could be Assigment directly, or a binexpr chained pointer expression (with '.' operator)
            if(parent is Assignment) {
                val dt = deref.inferType(program).getOrUndef()
                if(dt.isNumericOrBool) {
                    if (parent.value isSameAs deref) {
                        // get rid of ArrayIndexedPtrDereference in the assignment value
                        // x = z[i]^^ -->  x = peekX(z[i])
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
                        val indexed = ArrayIndexedExpression(identifier, null, null, indexer, deref.position)
                        val peekIdent = IdentifierReference(listOf(peekFunc), deref.position)
                        val peekCall = FunctionCallExpression(peekIdent, mutableListOf(indexed), deref.position)
                        if(cast==null)
                            return listOf(AstReplaceNode(parent.value, peekCall, parent))
                        else {
                            val casted = TypecastExpression(peekCall, cast, true, deref.position)
                            return listOf(AstReplaceNode(parent.value, casted, parent))
                        }
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
                        return listOf(AstReplaceNode(parent, deref, parent.parent))
                    }
                }
                //val dt = parent.inferType(program).getOrUndef()
                TODO("translate deref $deref  here ${deref.position}")
            }
            else if(parent is AssignTarget) {
                // get rid of ArrayIndexedPtrDereference in the assignment target
                // z[i]^^ = value -->  pokeX(z[i], value)
                val dt = deref.inferType(program).getOrUndef()
                if(dt.isNumericOrBool) {
                    // if it's something else beside number (like, a struct instance) we don't support rewriting that...
                    val (pokeFunc, cast) = pokeFunc(dt)
                    val indexer = deref.chain.last().second!!
                    val identifier = IdentifierReference(deref.chain.map { it.first }, deref.position)
                    val indexed = ArrayIndexedExpression(identifier, null, null, indexer, deref.position)
                    val pokeIdent = IdentifierReference(listOf(pokeFunc), deref.position)
                    val assignment = parent.parent as Assignment
                    val pokeCall: FunctionCallStatement
                    if (cast == null) {
                        pokeCall = FunctionCallStatement(
                            pokeIdent,
                            mutableListOf(indexed, assignment.value),
                            false,
                            deref.position
                        )
                    } else {
                        val casted = TypecastExpression(assignment.value, cast, true, deref.position)
                        pokeCall =
                            FunctionCallStatement(pokeIdent, mutableListOf(indexed, casted), false, deref.position)
                    }
                    return listOf(AstReplaceNode(assignment, pokeCall, assignment.parent))
                }
            }
            else {
                TODO("cannot translate $deref here ${deref.position}")
            }
        }


        val firstIndexed = deref.chain.indexOfFirst { it.second!=null }
        if(firstIndexed == 0 && deref.chain.size>1) {
            // z[i]^^.field   -->  (z[i]) . (field)

            val index = deref.chain.first()
            val tail = deref.chain.drop(1)
            if (tail.any { it.second != null }) {
                TODO("support multiple array indexed dereferencings  ${deref.position}")
            } else if (parent !is AssignTarget) {
                val pointer = IdentifierReference(listOf(index.first), deref.position)
                val left = ArrayIndexedExpression(pointer, null, null, index.second!!, deref.position)
                val right = PtrDereference(tail.map { it.first }, deref.derefLast, deref.position)
                val derefExpr = BinaryExpression(left, ".", right, deref.position)
                return listOf(AstReplaceNode(deref, derefExpr, parent))
            }
        }

        return noModifications
    }

    override fun before(assignTarget: AssignTarget, parent: Node): Iterable<AstModification> {
        if(assignTarget.dotExpression==null)
            return noModifications
        if(parent !is Assignment && parent !is ChainedAssignment)
            errors.err("cannot use a dereferenced expression as assignment target here", assignTarget.position)
        return noModifications
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<AstModification> {
        val target = assignment.target
        val dotExpr = target.dotExpression ?: return noModifications

        // decompose the '.' chain into the base expression and the field names
        val fields = mutableListOf<IdentifierReference>()
        var base = dotExpr
        while(base is BinaryExpression && base.operator==".") {
            val rightIdent = base.right as? IdentifierReference ?: return noModifications
            fields.add(rightIdent)
            base = base.left
        }
        fields.reverse()

        // Evaluate the base expression exactly once into a temporary variable, so that the
        // base is desugared in its own normal assignment context and the address computation
        // works on an already-evaluated simple value.
        // resolve the struct type of the base expression
        val baseDt = base.inferType(program).getOrUndef()
        // use the target's natural pointer width for the hoisted address (uword on 6502, long on 32-bit targets)
        val addressDt = if(program.target.POINTER_MEM_SIZE > 2u) DataType.LONG else DataType.UWORD
        val useTmpVar = baseDt.isPointer
        val tmpVar = if(useTmpVar) VarDecl.createAuto(addressDt, base.position) else null
        val tmpIdent = if(useTmpVar) IdentifierReference(listOf(tmpVar!!.name), base.position) else null
        val struct: StructDecl?
        val baseValue: Expression
        when {
            baseDt.isPointer -> {
                struct = baseDt.subType as? StructDecl
                if(struct==null) {
                    errors.err("cannot assign through this expression, expected a pointer to a struct", base.position)
                    return noModifications
                }
                // we only need the integer value of the pointer expression, so a redundant outer
                // pointer typecast can be stripped to avoid pointless double casts.
                val baseCast = base as? TypecastExpression
                baseValue = if(baseCast!=null && (baseCast.type.isPointer || baseCast.type.isUnsignedWord)
                        && baseCast.expression.inferType(program).getOrUndef().isIntegerOrBool)
                    TypecastExpression(baseCast.expression.copy(), addressDt, false, base.position)
                else
                    TypecastExpression(base.copy(), addressDt, false, base.position)
            }
            baseDt.isStructInstance -> {
                struct = baseDt.subType as? StructDecl
                if(struct==null) {
                    errors.err("cannot assign through this expression, expected a struct instance", base.position)
                    return noModifications
                }
                // base is a struct instance lvalue in memory; take its address
                baseValue = addressOfStructInstance(base, addressDt)
            }
            else -> {
                errors.err("cannot assign through this expression, expected a pointer to a struct or a struct instance", base.position)
                return noModifications
            }
        }
        val tmpAssign = if(useTmpVar) Assignment(
            AssignTarget(tmpIdent!!.copy(), null, null, null, false, position=tmpVar!!.position),
            baseValue,
            AssignmentOrigin.USERCODE, base.position) else null

        // build the address of the final field; intermediate pointer fields in the chain are loaded via peekw/peekl
        var address: Expression = if(useTmpVar) tmpIdent!!.copy() else baseValue
        var currentStruct: StructDecl = struct
        var fieldDt: DataType? = null
        for((index, field) in fields.withIndex()) {
            val fieldName = field.nameInSource.single()
            fieldDt = currentStruct.getFieldType(fieldName)
            if(fieldDt==null) {
                errors.err("no such field '$fieldName' in struct '${currentStruct.name}'", field.position)
                return noModifications
            }
            val offset = currentStruct.offsetof(fieldName, program.target)!!.toInt()
            if(offset>0)
                address = BinaryExpression(address, "+", NumericLiteral.optimalInteger(offset, field.position), field.position)
            if(index < fields.size-1) {
                if(!fieldDt.isPointer) {
                    errors.err("cannot dereference non-pointer field '$fieldName'", field.position)
                    return noModifications
                }
                val nextStruct = fieldDt.subType as? StructDecl
                if(nextStruct==null) {
                    errors.err("cannot dereference field '$fieldName', expected a pointer to a struct", field.position)
                    return noModifications
                }
                val peekName = if(program.target.POINTER_MEM_SIZE > 2u) "peekl" else "peekw"
                val peekCall = FunctionCallExpression(IdentifierReference(listOf(peekName), field.position), mutableListOf(address), field.position)
                address = TypecastExpression(peekCall, addressDt, false, field.position)
                currentStruct = nextStruct
            }
        }

        // select the appropriate poke/peek routine pair for the field's datatype
        val dt = fieldDt!!
        val funcName: String
        val pokeCast: DataType?
        val peekFuncName: String
        val peekCast: DataType?
        when {
            dt.isBool -> { funcName="pokebool"; pokeCast=null; peekFuncName="peekbool"; peekCast=null }
            dt.isUnsignedByte -> { funcName="poke"; pokeCast=null; peekFuncName="peek"; peekCast=null }
            dt.isSignedByte -> { funcName="poke"; pokeCast=DataType.UBYTE; peekFuncName="peek"; peekCast=DataType.BYTE }
            dt.isUnsignedWord -> { funcName="pokew"; pokeCast=null; peekFuncName="peekw"; peekCast=null }
            dt.isSignedWord -> { funcName="pokew"; pokeCast=DataType.UWORD; peekFuncName="peekw"; peekCast=DataType.WORD }
            dt.isLong -> { funcName="pokel"; pokeCast=null; peekFuncName="peekl"; peekCast=null }
            dt.isFloat -> { funcName="pokef"; pokeCast=null; peekFuncName="peekf"; peekCast=null }
            dt.isPointer -> {
                val ptrName = if(program.target.POINTER_MEM_SIZE > 2u) "l" else "w"
                funcName="poke$ptrName"; pokeCast=null; peekFuncName="peek$ptrName"; peekCast=dt
            }
            else -> {
                errors.err("unsupported field datatype $dt for write through a pointer", target.position)
                return noModifications
            }
        }

        // augmented assignment pattern: the value starts with (a copy of) the very same dotted chain,
        // e.g. "(p).bar += 1". Replace that copy by a read of the field through the temporary address,
        // so that everything is evaluated exactly once.
        var valueExpr = assignment.value
        val binValue = valueExpr as? BinaryExpression
        if(binValue!=null && binValue.left isSameAs dotExpr) {
            val peekCall = FunctionCallExpression(
                IdentifierReference(listOf(peekFuncName), binValue.left.position),
                mutableListOf(address.copy()), binValue.left.position)
            val readExpr: Expression = if(peekCast==null) peekCall else TypecastExpression(peekCall, peekCast, false, peekCall.position)
            valueExpr = BinaryExpression(readExpr, binValue.operator, binValue.right, binValue.position)
        }
        val value = if(pokeCast==null) valueExpr else TypecastExpression(valueExpr, pokeCast, false, valueExpr.position)

        val pokeCall = FunctionCallStatement(
            IdentifierReference(listOf(funcName), assignment.position),
            mutableListOf(address, value), false, assignment.position)
        return if(useTmpVar) {
            val replacement = AnonymousScope(mutableListOf(tmpVar!!, tmpAssign!!, pokeCall), target.position)
            listOf(AstReplaceNode(assignment, replacement, parent))
        } else {
            listOf(AstReplaceNode(assignment, pokeCall, parent))
        }
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<AstModification> {
        val targetDt = assignment.target.inferType(program)
        val sourceDt = assignment.value.inferType(program)
        if(targetDt.isStructInstance && sourceDt.isStructInstance) {
            if(targetDt == sourceDt) {
                // special case simple struct instance assignment via memory copy
                val size = program.target.memorySize(sourceDt.getOrUndef(), null)
                val structSizeNum = NumericLiteral.optimalInteger(size, assignment.position)
                val deref = assignment.value as? PtrDereference
                if(deref!=null) {
                    val sourcePtr = IdentifierReference(deref.chain, assignment.position)
                    val targetDeref = assignment.target.pointerDereference
                    if(targetDeref!=null) {
                        // ptr1^^ = ptr2^^   -->    memcopy(ptr2, ptr1, sizeof(struct))
                        val targetPtr = IdentifierReference(targetDeref.chain, assignment.position)
                        val memcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "memcopy"), assignment.position),
                            mutableListOf(sourcePtr, targetPtr, structSizeNum),
                            false, assignment.position)
                        return listOf(AstReplaceNode(assignment, memcopy, parent))
                    }
                    val targetIdx = assignment.target.arrayindexed
                    if(targetIdx!=null) {
                        val idxType = targetIdx.inferType(program)
                        if (idxType.isStructInstance) {
                            // ptr1[idx]^^ = ptr2^^   -->    memcopy(ptr2, ptr1+idx, sizeof(struct))        ; relies on pointer arithmetic
                            val target = BinaryExpression(targetIdx.plainarrayvar!!, "+", targetIdx.indexer.indexExpr, assignment.position)
                            val memcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "memcopy"), assignment.position),
                                mutableListOf(sourcePtr, target, structSizeNum),
                                false, assignment.position)
                            return listOf(AstReplaceNode(assignment, memcopy, parent))
                        }
                    }
                    val indexedDeref = assignment.target.arrayIndexedDereference
                    if(indexedDeref!=null) {
                        val idxType = indexedDeref.inferType(program)
                        if (idxType.isStructInstance && indexedDeref.chain.size==1 && indexedDeref.derefLast) {
                            // ptr[idx]^^ = ptr2^^   -->  memcopy(ptr2, ptr1+idx, sizeof(struct))        ; relies on pointer arithmetic
                            val arrayvar = IdentifierReference(listOf(indexedDeref.chain[0].first), indexedDeref.position)
                            val index = indexedDeref.chain[0].second!!.indexExpr
                            val target = BinaryExpression(arrayvar, "+", index, assignment.position)
                            val memcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "memcopy"), assignment.position),
                                mutableListOf(sourcePtr, target, structSizeNum),
                                false, assignment.position)
                            return listOf(AstReplaceNode(assignment, memcopy, parent))
                        }
                    }
                }

                val sourceIdx = assignment.value as? ArrayIndexedExpression
                if(sourceIdx!=null) {
                    val idxType = sourceIdx.inferType(program)
                    if(idxType.isStructInstance) {
                        val targetDeref = assignment.target.pointerDereference
                        if(targetDeref!=null) {
                            // ptr1^^ = ptr2[idx]  -->  memcopy(ptr2 + idx, ptr1, sizeof(struct))       ; relies on pointer arithmetic
                            val targetPtr = IdentifierReference(targetDeref.chain, assignment.position)
                            val source = BinaryExpression(sourceIdx.plainarrayvar!!, "+", sourceIdx.indexer.indexExpr, assignment.position)
                            val memcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "memcopy"), assignment.position),
                                mutableListOf(source, targetPtr, structSizeNum),
                                false, assignment.position)
                            return listOf(AstReplaceNode(assignment, memcopy, parent))
                        }
                        // points[1] = points[2]  -->  memcopy(&points[2], &points[1], sizeof(struct))
                        val targetIdx = assignment.target.arrayindexed
                        if(targetIdx!=null) {
                            val tIdxType = targetIdx.inferType(program)
                            if(tIdxType.isStructInstance && tIdxType == idxType) {
                                val source = AddressOf(sourceIdx.plainarrayvar!!.copy(), sourceIdx.indexer.copy(), null, false, true, assignment.position)
                                val target = AddressOf(targetIdx.plainarrayvar!!.copy(), targetIdx.indexer.copy(), null, false, true, assignment.position)
                                val memcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "memcopy"), assignment.position),
                                    mutableListOf(source, target, structSizeNum),
                                    false, assignment.position)
                                return listOf(AstReplaceNode(assignment, memcopy, parent))
                            }
                        }
                    }
                }

                val sourceIndexDeref = assignment.value as? ArrayIndexedPtrDereference
                if(sourceIndexDeref!=null) {
                    val idxType = sourceIndexDeref.inferType(program)
                    if(idxType.isStructInstance && sourceIndexDeref.chain.size==1 && sourceIndexDeref.derefLast && sourceIndexDeref.chain[0].second?.indexExpr!=null) {
                        val targetDeref = assignment.target.pointerDereference
                        if(targetDeref!=null) {
                            // ptr1^^ = ptr2[idx]^^   -->  memcopy(ptr2 + idx, ptr1, sizeof(struct))       ; relies on pointer arithmetic
                            val chain = sourceIndexDeref.chain.map { it.first }
                            val sourcePtr = IdentifierReference(chain, assignment.position)
                            val source = BinaryExpression(sourcePtr, "+", sourceIndexDeref.chain[0].second!!.indexExpr, assignment.position)
                            val targetPtr = IdentifierReference(targetDeref.chain, assignment.position)
                            val memcopy = FunctionCallStatement(
                                IdentifierReference(listOf("sys", "memcopy"), assignment.position),
                                mutableListOf(source, targetPtr, structSizeNum),
                                false, assignment.position
                            )
                            return listOf(AstReplaceNode(assignment, memcopy, parent))
                        }
                    }
                }
            }
        }

        if(assignment.target.pointerDereference!=null && assignment.value is PtrDereference) {
            val targetPtr = assignment.target.pointerDereference!!
            val sourcePtr = assignment.value as PtrDereference
            val targetDt = targetPtr.inferType(program)
            if (targetDt == sourcePtr.inferType(program)) {
                val sourceAddress: Expression
                val targetAddress: Expression
                if(!targetPtr.derefLast) {
                    val targetIdentifier = IdentifierReference(targetPtr.chain, assignment.position)
                    targetAddress = AddressOf(targetIdentifier, null, null, false, false, assignment.position)
                } else
                    targetAddress = IdentifierReference(targetPtr.chain, assignment.position)
                if(!sourcePtr.derefLast) {
                    val sourceIdentifier = IdentifierReference(sourcePtr.chain, assignment.position)
                    sourceAddress = AddressOf(sourceIdentifier, null, null, false, false, assignment.position)
                } else
                    sourceAddress = IdentifierReference(sourcePtr.chain, assignment.position)
                val copy = when {
                    targetDt.isLong -> FunctionCallStatement(
                        IdentifierReference(listOf("prog8_lib_copylong"), assignment.position),
                        mutableListOf(sourceAddress, targetAddress), false, assignment.position
                    )
                    targetDt.isFloat -> FunctionCallStatement(
                        IdentifierReference(listOf("prog8_lib_copyfloat"), assignment.position),
                        mutableListOf(sourceAddress, targetAddress), false, assignment.position
                    )
                    else -> null
                }

                if(copy!=null)
                    return listOf(AstReplaceNode(assignment, copy, parent))
            }
        }

        return noModifications
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<AstModification> {
        val error = checkCondition(ifElse.condition)
        if(error!=null)
            errors.err(error, ifElse.condition.position)
        return noModifications
    }

    override fun after(ifExpr: IfExpression, parent: Node): Iterable<AstModification> {
        val error = checkCondition(ifExpr.condition)
        if(error!=null)
            errors.err(error, ifExpr.condition.position)
        return noModifications
    }

    override fun after(array: ArrayLiteral, parent: Node): Iterable<AstModification> {

        fun convertArrayIntoStructInitializer(array: ArrayLiteral, struct: ISubType, isPointer: Boolean): StaticStructInitializer {
            val structname = IdentifierReference(struct.scopedNameString.split("."), array.position)
            return StaticStructInitializer(structname, array.value.toMutableList(), array.position, isPointer)
        }

        if(parent is VarDecl) {
            if(!parent.datatype.isArray) return noModifications
            val elemDt = parent.datatype.elementType()
            val isStructPointerElem = elemDt.isPointer && elemDt.subType!=null
            val isStructInstanceElem = elemDt.isStructInstance && elemDt.subType!=null
            if (isStructPointerElem || isStructInstanceElem) {
                val struct = elemDt.subType as StructDecl
                val isPointer = isStructPointerElem
                val allremovals = mutableListOf<VarDecl>()
                var changes = false
                array.value.withIndex().forEach { (index, elt) ->
                    if(elt is ArrayLiteral) {
                        array.value[index] = convertArrayIntoStructInitializer(elt, struct, isPointer)
                        changes = true
                    } else if(elt is IdentifierReference) {
                        val arrayvar = elt.targetVarDecl()!!.value as ArrayLiteral
                        array.value[index] = convertArrayIntoStructInitializer(arrayvar, struct, isPointer)
                        allremovals += elt.targetVarDecl()!!
                        changes = true
                    }
                }

                if(changes) {
                    array.linkParents(parent)
                    return allremovals.map { AstRemove(it, it.parent as IStatementContainer) }
                }
            }
        }
        else if(parent is Assignment) {
            val targetDt = parent.target.inferType(program).getOrUndef()
            if(targetDt.isPointer && targetDt.subType!=null) {
                val struct = targetDt.subType as StructDecl
                val initializser = convertArrayIntoStructInitializer(array, struct, true)
                return listOf(AstReplaceNode(array, initializser, parent))
            }
        }

        return noModifications
    }

    private fun addressOfStructInstance(base: Expression, addressDt: DataType): Expression {
        val addr = when(base) {
            is IdentifierReference -> AddressOf(base.copy(), null, null, false, true, base.position)
            is ArrayIndexedExpression -> AddressOf(
                base.plainarrayvar?.copy(), base.indexer.copy(), null, false, true, base.position)
            is TypecastExpression -> return addressOfStructInstance(base.expression, addressDt)
            else -> {
                errors.err("cannot take address of struct instance base expression $base", base.position)
                // return a dummy to keep desugaring moving; AstChecker will catch the error later
                AddressOf(IdentifierReference(listOf("_dummy"), base.position), null, null, false, true, base.position)
            }
        }
        return TypecastExpression(addr, addressDt, false, base.position)
    }
}
