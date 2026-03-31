package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.*
import prog8.code.ast.PtContainmentCheck
import prog8.code.core.IErrorReporter


internal class LiteralsToAutoVarsAndRecombineIdentifiers(private val program: Program, private val errors: IErrorReporter) : AstWalker() {

    override fun after(string: StringLiteral, parent: Node): Iterable<AstModification> {
        if(string.parent !is VarDecl && string.parent !is WhenChoice) {
            val binExpr = string.parent as? BinaryExpression
            if(binExpr!=null &&(binExpr.operator=="+" || binExpr.operator=="*"))
                return noModifications // allow string concatenation or repeats later, based on just string literals

            // replace the literal string by an identifier reference to the interned string
            val parentFunc = (string.parent as? IFunctionCall)?.target
            if(parentFunc!=null) {
                if(parentFunc.nameInSource.size==1 && parentFunc.nameInSource[0]=="memory") {
                    // memory() builtin function just uses the string as a label name
                    return noModifications
                }
            }
            val scopedName = program.internString(string)
            val identifier = IdentifierReference(scopedName, string.position)
            return listOf(AstReplaceNode(string, identifier, parent))
        }
        return noModifications
    }

    override fun after(array: ArrayLiteral, parent: Node): Iterable<AstModification> {
        val vardecl = array.parent as? VarDecl
        if(vardecl!=null) {
            // adjust the datatype of the array (to an educated guess from the vardecl type)
            val arrayDt = array.type
            if(!(arrayDt istype vardecl.datatype)) {
                val cast = array.cast(vardecl.datatype)
                if(cast!=null && cast !== array)
                    return listOf(AstReplaceNode(vardecl.value!!, cast, vardecl))
            }
        } else {
            val arrayDt = array.guessDatatype(program)
            if(arrayDt.isUnknown)
                return noModifications
            val elementDt = arrayDt.getOrUndef().elementType()
            val maxSize = when {
                elementDt.isByteOrBool -> PtContainmentCheck.MAX_SIZE_FOR_INLINE_CHECKS_BYTE
                elementDt.isWord -> PtContainmentCheck.MAX_SIZE_FOR_INLINE_CHECKS_WORD
                else -> 0
            }
            if(parent is ContainmentCheck && array.value.size <= maxSize) {
                // keep the array in the containmentcheck inline
                return noModifications
            }
            if(arrayDt.isKnown) {
                if((array.parent as? BinaryExpression)?.operator!="*") {
                    val parentAssign = parent as? Assignment
                    val targetDt = parentAssign?.target?.inferType(program) ?: arrayDt
                    // turn the array literal it into an identifier reference
                    val litval2 = array.cast(targetDt.getOrUndef())
                    if (litval2 != null) {
                        val vardecl2 = VarDecl.createAuto(litval2)
                        val identifier = IdentifierReference(listOf(vardecl2.name), vardecl2.position)
                        return listOf(
                            AstReplaceNode(array, identifier, parent),
                            AstInsertFirst(array.definingScope, vardecl2)
                        )
                    }
                }
            }
        }

        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<AstModification> {
        if(decl.names.size>1) {

            val fcall = decl.value as? IFunctionCall
            val fcallTarget = fcall?.target?.targetSubroutine()
            val isBuiltinMultiReturn = fcall?.target?.let { target ->
                val name = target.nameInSource.singleOrNull()
                name != null && name in program.builtinFunctions.names && program.builtinFunctions.returnTypes(name).size > 1
            } ?: false
            if(fcallTarget!=null || isBuiltinMultiReturn) {
                // ubyte a,b,c = multi() --> ubyte a,b,c / a,b,c = multi()
                // also handles builtin functions that return multiple values (like divmod)
                val modifications = mutableListOf<AstModification>()
                val variables = decl.names.map {
                    AssignTarget(
                        IdentifierReference(listOf(it), decl.position),
                        null,
                        null,
                        null,
                        false,
                        position = decl.position
                    )
                }
                val multiassignFuncCall = Assignment(AssignTarget(
                    null,
                    null,
                    null,
                    variables,
                    false,
                    position = decl.position
                ), decl.value!!, AssignmentOrigin.VARINIT, decl.position)
                decl.value = null
                modifications += AstInsertAfter(parent as IStatementContainer, multiassignFuncCall, decl)
                return modifications
            }

            // note: the desugaring of a multi-variable vardecl has to be done here
            // and not in CodeDesugarer, that one is too late (identifiers can't be found otherwise)
            if(!decl.datatype.isNumericOrBool && !decl.datatype.isPointer && !decl.datatype.isStructInstance)
                errors.err("cannot multi declare variables of this type", decl.position)
            if(decl.alignment != 0u) {
                errors.err("only single variable declarations can have alignment", decl.position)
                return noModifications
            }

            if(errors.noErrors()) {
                // desugar into individual vardecl per name.
                return decl.desugarMultiDecl().map {
                    AstInsertBefore(parent as IStatementContainer, it, decl)
                } + AstRemove(decl, parent as IStatementContainer)
            }
        }
        return noModifications
    }

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<AstModification> {
        val target = identifier.targetStatement(program.builtinFunctions)

        if(target is StructFieldRef) {
            // replace a.b.c.d   by  a^^.b^^.c^^.d
            // but only if we're not part of an alias or a binary expression with '.' operator (those are handled elsewhere)
            if(parent !is Alias && (parent !is BinaryExpression || parent.operator != ".")) {
                val chain = identifier.nameInSource
                val deref = PtrDereference(chain, false, identifier.position)
                return listOf(AstReplaceNode(identifier, deref, parent))
            }
        }

        if(target==null && identifier.nameInSource.size>1) {
            // First component might be an alias
            val tgt2 = identifier.definingScope.lookup(identifier.nameInSource.take(1)) as? Alias
            if(tgt2!=null && parent !is Alias) {
                val aliasTarget = resolveAliasTarget(tgt2)
                if(aliasTarget != null) {
                    val actual = when(aliasTarget) {
                        is INamedStatement -> IdentifierReference(aliasTarget.scopedName + identifier.nameInSource.drop(1), identifier.position)
                        else -> IdentifierReference(tgt2.target.nameInSource + identifier.nameInSource.drop(1), identifier.position)
                    }
                    return listOf(AstReplaceNode(identifier, actual, parent))
                }
            }
        }

        if(target is Alias && parent !is Alias) {
            val targetStatement = resolveAliasTarget(target)
            if(targetStatement == null) {
                return noModifications  // alias loop or unresolved
            }
            val replacement: IdentifierReference =
                when (targetStatement) {
                    is StructFieldRef -> {
                        target.target.copy(position = identifier.position)
                    }
                    is VarDecl -> {
                        val scoped = if (targetStatement.names.isNotEmpty()) {
                            (targetStatement.parent as INamedStatement).scopedName + target.target.nameInSource.last()
                        } else {
                            (targetStatement as INamedStatement).scopedName
                        }
                        require(scoped.last() != "<multiple>")
                        IdentifierReference(scoped, identifier.position)
                    }
                    is BuiltinFunctionPlaceholder -> {
                        IdentifierReference(listOf(targetStatement.name), identifier.position)
                    }
                    else -> {
                        val scoped = (targetStatement as INamedStatement).scopedName
                        require(scoped.last() != "<multiple>")
                        IdentifierReference(scoped, identifier.position)
                    }
                }

            return listOf(AstReplaceNode(identifier, replacement, parent))
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<AstModification> {
        if(expr.operator==".") {
            val leftIdent = expr.left as? IdentifierReference
            val rightIndex = expr.right as? ArrayIndexedExpression
            if (leftIdent != null && rightIndex != null) {
                // maybe recombine   IDENTIFIER . ARRAY[IDX]  -->  COMBINEDIDENTIFIER[IDX]
                val leftTarget = leftIdent.targetStatement(program.builtinFunctions)
                if(leftTarget==null || leftTarget !is StructDecl) {
                    if(rightIndex.plainarrayvar!=null) {
                        val combinedName = leftIdent.nameInSource + rightIndex.plainarrayvar!!.nameInSource
                        val combined = IdentifierReference(combinedName, leftIdent.position)
                        val indexer = ArrayIndexedExpression(combined, null, rightIndex.indexer, leftIdent.position)
                        return listOf(AstReplaceNode(expr, indexer, parent))
                    } else {
                        throw FatalAstException("didn't expect pointer[idx] in this phase already")
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(deref: PtrDereference, parent: Node): Iterable<AstModification> {
        val tgt2 = deref.definingScope.lookup(deref.chain.take(1)) as? Alias
        if(tgt2!=null && parent !is Alias) {
            val aliasTarget = resolveAliasTarget(tgt2)
            if(aliasTarget != null) {
                val unaliased = when(aliasTarget) {
                    is INamedStatement -> PtrDereference(aliasTarget.scopedName + deref.chain.drop(1), deref.derefLast, deref.position)
                    else -> PtrDereference(tgt2.target.nameInSource + deref.chain.drop(1), deref.derefLast, deref.position)
                }
                return listOf(AstReplaceNode(deref, unaliased, parent))
            }
        }
        return noModifications
    }

    /** Follows alias chains, returns null on loop or unresolved. */
    private fun resolveAliasTarget(alias: Alias): Statement? {
        var currentAlias = alias
        val visited = mutableSetOf<String>()
        var hops = 0
        val maxHops = 100

        while (hops < maxHops) {
            val aliasKey = currentAlias.alias + "->" + currentAlias.target.nameInSource.joinToString(".")
            if (aliasKey in visited) {
                return null  // alias loop detected
            }
            visited.add(aliasKey)

            val target = currentAlias.target.targetStatement(program.builtinFunctions) ?: return null
            if (target !is Alias) {
                return target  // resolved to non-alias
            }
            currentAlias = target
            hops++
        }
        return null  // likely loop
    }
}
