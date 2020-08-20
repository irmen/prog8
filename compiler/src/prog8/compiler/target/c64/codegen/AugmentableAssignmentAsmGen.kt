package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.IterableDatatypes
import prog8.ast.expressions.*
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.C64MachineDefinition.C64Zeropage
import prog8.compiler.toHex

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
            is TypecastExpression -> inplaceCast(assign.target, assign.value as TypecastExpression, assign)
            is BinaryExpression -> inplaceBinary(assign.target, assign.value as BinaryExpression, assign)
            else -> throw AssemblyError("invalid aug assign value type")
        }
    }

    private fun inplaceBinary(target: AssignTarget, binaryExpression: BinaryExpression, assign: Assignment) {
        println("TODO optimize binexpr assignment ${binaryExpression.position}")
        assignmentAsmGen.translateOtherAssignment(assign) // TODO get rid of this fallback
    }

    private fun inplaceCast(target: AssignTarget, cast: TypecastExpression, assign: Assignment) {
        val targetDt = target.inferType(program, assign).typeOrElse(DataType.STRUCT)
        val outerCastDt = cast.type
        val innerCastDt = (cast.expression as? TypecastExpression)?.type

        if(innerCastDt==null) {
            // simple typecast where the value is the target
            when(targetDt) {
                DataType.UBYTE, DataType.BYTE -> { /* byte target can't be casted to anything else at all */ }
                DataType.UWORD, DataType.WORD -> {
                    when(outerCastDt) {
                        DataType.UBYTE, DataType.BYTE -> {
                            if(target.identifier!=null) {
                                val name = asmgen.asmIdentifierName(target.identifier!!)
                                asmgen.out(" lda  #0 |  sta  $name+1")
                            } else
                                throw AssemblyError("weird value")
                        }
                        DataType.UWORD, DataType.WORD, in IterableDatatypes -> {}
                        DataType.FLOAT -> throw AssemblyError("incompatible cast type")
                        else -> throw AssemblyError("weird cast type")
                    }
                }
                DataType.FLOAT -> {
                    if(outerCastDt!=DataType.FLOAT)
                        throw AssemblyError("in-place cast of a float makes no sense")
                }
                else -> throw AssemblyError("invalid cast target type")
            }
        } else {
            // typecast with nested typecast, that has the target as a value
            // calculate singular cast that is required
            val castDt = if(outerCastDt largerThan innerCastDt) innerCastDt else outerCastDt
            val value = (cast.expression as TypecastExpression).expression
            val resultingCast = TypecastExpression(value, castDt, false, assign.position)
            inplaceCast(target, resultingCast, assign)
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
                        when (memory.addressExpression) {
                            is NumericLiteralValue -> {
                                val addr = (memory.addressExpression as NumericLiteralValue).number.toHex()
                                asmgen.out("""
                                    lda  $addr
                                    beq  +
                                    lda  #1
+                                   eor  #1
                                    sta  $addr""")
                            }
                            is IdentifierReference -> {
                                val name = asmgen.asmIdentifierName(memory.addressExpression as IdentifierReference)
                                asmgen.out("""
                                    lda  $name
                                    sta  ${C64Zeropage.SCRATCH_W1}
                                    lda  $name+1
                                    sta  ${C64Zeropage.SCRATCH_W1+1}
                                    ldy  #0
                                    lda  (${C64Zeropage.SCRATCH_W1}),y
                                    beq  +
                                    lda  #1
+                                   eor  #1
                                    sta  (${C64Zeropage.SCRATCH_W1}),y""")
                            }
                            else -> throw AssemblyError("weird address value")
                        }
                    }
                    arrayIdx!=null -> {
                        TODO("in-place not of ubyte array")
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
                    arrayIdx!=null -> TODO("in-place not of uword array")
                    memory!=null -> throw AssemblyError("no asm gen for uword-memory not")
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
                        when (memory.addressExpression) {
                            is NumericLiteralValue -> {
                                val addr = (memory.addressExpression as NumericLiteralValue).number.toHex()
                                asmgen.out("""
                                    lda  $addr
                                    eor  #255
                                    sta  $addr""")
                            }
                            is IdentifierReference -> {
                                val name = asmgen.asmIdentifierName(memory.addressExpression as IdentifierReference)
                                asmgen.out("""
                                    lda  $name
                                    sta  ${C64Zeropage.SCRATCH_W1}
                                    lda  $name+1
                                    sta  ${C64Zeropage.SCRATCH_W1+1}
                                    ldy  #0
                                    lda  (${C64Zeropage.SCRATCH_W1}),y
                                    eor  #255
                                    sta  (${C64Zeropage.SCRATCH_W1}),y""")
                            }
                            else -> throw AssemblyError("weird address value")
                        }                    }
                    arrayIdx!=null -> {
                        TODO("in-place invert ubyte array")
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
                    arrayIdx!=null -> TODO("in-place invert uword array")
                    memory!=null -> throw AssemblyError("no asm gen for uword-memory invert")
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
                    memory!=null -> throw AssemblyError("can't in-place negate memory ubyte")
                    arrayIdx!=null -> TODO("in-place negate byte array")
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
                    arrayIdx!=null -> TODO("in-place negate word array")
                    memory!=null -> throw AssemblyError("no asm gen for word memory negate")
                }
            }
            DataType.FLOAT -> {
                when {
                    identifier!=null -> {
                        val name = asmgen.asmIdentifierName(identifier)
                        asmgen.out("""
                            stx  ${C64Zeropage.SCRATCH_REG_X}
                            lda  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVFM
                            jsr  c64flt.NEGOP
                            ldx  #<$name
                            ldy  #>$name
                            jsr  c64flt.MOVMF
                            ldx  ${C64Zeropage.SCRATCH_REG_X}
                        """)
                    }
                    arrayIdx!=null -> TODO("in-place negate float array")
                    memory!=null -> throw AssemblyError("no asm gen for float memory negate")
                }
            }
            else -> throw AssemblyError("negate of invalid type")
        }
    }

}
