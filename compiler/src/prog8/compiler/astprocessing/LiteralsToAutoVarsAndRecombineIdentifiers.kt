package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.ast.PtContainmentCheck
import prog8.code.core.IErrorReporter


internal class LiteralsToAutoVarsAndRecombineIdentifiers(private val program: Program, private val errors: IErrorReporter) : AstWalker() {

    override fun after(string: StringLiteral, parent: Node): Iterable<IAstModification> {
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
            return listOf(IAstModification.ReplaceNode(string, identifier, parent))
        }
        return noModifications
    }

    override fun after(array: ArrayLiteral, parent: Node): Iterable<IAstModification> {
        val vardecl = array.parent as? VarDecl
        if(vardecl!=null) {
            // adjust the datatype of the array (to an educated guess from the vardecl type)
            val arrayDt = array.type
            if(!(arrayDt istype vardecl.datatype)) {
                val cast = array.cast(vardecl.datatype)
                if(cast!=null && cast !== array)
                    return listOf(IAstModification.ReplaceNode(vardecl.value!!, cast, vardecl))
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
                            IAstModification.ReplaceNode(array, identifier, parent),
                            IAstModification.InsertFirst(vardecl2, array.definingScope)
                        )
                    }
                }
            }
        }

        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(decl.names.size>1) {

            val fcallTarget = (decl.value as? IFunctionCall)?.target?.targetSubroutine()
            if(fcallTarget!=null) {
                // ubyte a,b,c = multi() --> ubyte a,b,c / a,b,c = multi()
                val modifications = mutableListOf<IAstModification>()
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
                modifications += IAstModification.InsertAfter(decl, multiassignFuncCall, parent as IStatementContainer)
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
                    IAstModification.InsertBefore(decl, it, parent as IStatementContainer)
                } + IAstModification.Remove(decl, parent as IStatementContainer)
            }
        }
        return noModifications
    }

    override fun after(alias: Alias, parent: Node): Iterable<IAstModification> {
        val target = alias.target.targetStatement()
        if(target is Alias) {
            // shortcut the alias that refers to another alias
            val newAlias = Alias(alias.alias, target.target, alias.position)
            return listOf(IAstModification.ReplaceNode(alias, newAlias, parent))
        }
        return noModifications
    }

    override fun after(identifier: IdentifierReference, parent: Node): Iterable<IAstModification> {
        val target = identifier.targetStatement()

        if(target is StructFieldRef) {
            // replace a.b.c.d   by  a^^.b^^.c^^.d
            // but only if we're not part of an alias or a binary expression with '.' operator (those are handled elsewhere)
            if(parent !is Alias && (parent !is BinaryExpression || parent.operator != ".")) {
                val chain = identifier.nameInSource
                val deref = PtrDereference(chain, false, identifier.position)
                return listOf(IAstModification.ReplaceNode(identifier, deref, parent))
            }
        }

        if(target==null && identifier.nameInSource.size>1) {
            // maybe the first component of the scoped name is an alias?
            val tgt2 = identifier.definingScope.lookup(identifier.nameInSource.take(1)) as? Alias
            if(tgt2!=null && parent !is Alias) {
                if(tgt2.target.targetStatement() !is Alias) {
                    val actual = IdentifierReference(tgt2.target.nameInSource + identifier.nameInSource.drop(1), identifier.position)
                    return listOf(IAstModification.ReplaceNode(identifier, actual, parent))
                }
            }
        }

        // don't replace an identifier in an Alias or when the alias points to another alias (that will be resolved first elsewhere)
        if(target is Alias && parent !is Alias) {
            if(target.target.targetStatement() !is Alias)
                return listOf(IAstModification.ReplaceNode(identifier, target.target.copy(position = identifier.position), parent))
        }

// experimental code to be able to alias blocks too:
//        if(target is INamedStatement) {
//            if (identifier.nameInSource != target.scopedName) {
//                val blockAlias = identifier.definingScope.lookup(identifier.nameInSource.take(1))
//                if(blockAlias is Alias) {
//                    val newname = mutableListOf(blockAlias.target.nameInSource.single())
//                    newname.addAll(identifier.nameInSource.drop(1))
//                    return listOf(IAstModification.ReplaceNode(identifier, IdentifierReference(newname, position = identifier.position), parent))
//                }
//            }
//        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        if(expr.operator==".") {
            val leftIdent = expr.left as? IdentifierReference
            val rightIndex = expr.right as? ArrayIndexedExpression
            if (leftIdent != null && rightIndex != null) {
                // maybe recombine   IDENTIFIER . ARRAY[IDX]  -->  COMBINEDIDENTIFIER[IDX]
                val leftTarget = leftIdent.targetStatement()
                if(leftTarget==null || leftTarget !is StructDecl) {
                    if(rightIndex.plainarrayvar!=null) {
                        val combinedName = leftIdent.nameInSource + rightIndex.plainarrayvar!!.nameInSource
                        val combined = IdentifierReference(combinedName, leftIdent.position)
                        val indexer = ArrayIndexedExpression(combined, null, rightIndex.indexer, leftIdent.position)
                        return listOf(IAstModification.ReplaceNode(expr, indexer, parent))
                    } else {
                        throw FatalAstException("didn't expect pointer[idx] in this phase already")
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(deref: ArrayIndexedPtrDereference, parent: Node): Iterable<IAstModification> {
        // TODO handle aliases?
        return noModifications
    }

    override fun after(deref: PtrDereference, parent: Node): Iterable<IAstModification> {
        // handle aliases
        // maybe the first component of the dereference chain is an alias?
        val tgt2 = deref.definingScope.lookup(deref.chain.take(1)) as? Alias
        if(tgt2!=null && parent !is Alias) {
            if(tgt2.target.targetStatement() !is Alias) {
                val unaliased = PtrDereference(tgt2.target.nameInSource + deref.chain.drop(1), deref.derefLast, deref.position)
                return listOf(IAstModification.ReplaceNode(deref, unaliased, parent))
            }
        }
        return noModifications
    }
}
