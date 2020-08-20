package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.PrefixExpression
import prog8.ast.expressions.TypecastExpression
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.compiler.AssemblyError

internal class AugmentableAssignmentAsmGen(private val program: Program,
                                  private val assignmentAsmGen: AssignmentAsmGen,
                                  private val asmgen: AsmGen) {
    fun translate(assign: Assignment) {
        require(assign.isAugmentable)

        when (assign.value) {
            is PrefixExpression -> {
                // A = -A , A = +A, A = ~A
                val px = assign.value as PrefixExpression
                val type = px.inferType(program).typeOrElse(DataType.STRUCT)
                when(px.operator) {
                    "+" -> {}
                    "-" -> inplaceNegate(assign.target, type)
                    "~" -> inplaceInvert(assign.target, type)
                    "not" -> inplaceBooleanNot(assign.target, type)
                    else -> throw AssemblyError("invalid prefix operator")
                }
            }
            is TypecastExpression -> {
                println("TODO optimize typecast assignment ${assign.position}")
                assignmentAsmGen.translateOtherAssignment(assign) // TODO get rid of this fallback
            }
            is BinaryExpression -> {
                println("TODO optimize binexpr assignment ${assign.position}")
                assignmentAsmGen.translateOtherAssignment(assign) // TODO get rid of this fallback
            }
            else -> {
                throw AssemblyError("invalid aug assign value type")
            }
        }
    }

    private fun inplaceBooleanNot(target: AssignTarget, dt:  DataType) {
        val arrayIdx = target.arrayindexed
        val identifier = target.identifier
        val memory = target.memoryAddress

        when(dt) {
            DataType.UBYTE -> {
                when {
                    identifier!=null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  $name
                            beq  +
                            lda  #1
+                           eor  #1
                            sta  $name""")
                    }
                    memory!=null -> {
                        TODO()
                    }
                    arrayIdx!=null -> {
                        TODO()
                    }
                }
            }
            DataType.UWORD -> {
                when {
                    identifier!=null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  $name
                            ora  $name+1
                            beq  +
                            lda  #1
+                           eor  #1
                            sta  $name
                            lsr  a
                            sta  $name+1""")
                    }
                    memory!=null -> {
                        TODO()
                    }
                    arrayIdx!=null -> {
                        TODO()
                    }
                }
            }
            else -> throw AssemblyError("boolean-not of invalid type")
        }
    }

    private fun inplaceInvert(target: AssignTarget, dt: DataType) {
        val arrayIdx = target.arrayindexed
        val identifier = target.identifier
        val memory = target.memoryAddress

        when(dt) {
            DataType.UBYTE -> {
                when {
                    identifier!=null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  $name
                            eor  #255
                            sta  $name""")
                    }
                    memory!=null -> {
                        TODO()
                    }
                    arrayIdx!=null -> {
                        TODO()
                    }
                }
            }
            DataType.UWORD -> {
                when {
                    identifier!=null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  $name
                            eor  #255
                            sta  $name
                            lda  $name+1
                            eor  #255
                            sta  $name+1""")
                    }
                    memory!=null -> {
                        TODO()
                    }
                    arrayIdx!=null -> {
                        TODO()
                    }
                }
            }
            else -> throw AssemblyError("invert of invalid type")
        }
    }

    private fun inplaceNegate(target: AssignTarget, dt: DataType) {
        val arrayIdx = target.arrayindexed
        val identifier = target.identifier
        val memory = target.memoryAddress

        when(dt) {
            DataType.BYTE -> {
                when {
                    identifier!=null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  #0
                            sec
                            sbc  $name
                            sta  $name""")
                    }
                    memory!=null -> {
                        TODO()
                    }
                    arrayIdx!=null -> {
                        TODO()
                    }
                }
            }
            DataType.WORD -> {
                when {
                    identifier!=null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            lda  #0
                            sec
                            sbc  $name
                            sta  $name
                            lda  #0
                            sbc  $name+1
                            sta  $name+1""")
                    }
                    memory!=null -> {
                        TODO()
                    }
                    arrayIdx!=null -> {
                        TODO()
                    }
                }
            }
            DataType.FLOAT -> {
                TODO()
            }
            else -> throw AssemblyError("negate of invalid type")
        }
    }

}
