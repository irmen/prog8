package prog8.codegen.cpu6502

import prog8.code.ast.*
import prog8.code.core.*

internal class IfElseAsmGen(private val program: PtProgram, private val asmgen: AsmGen6502Internal) {

    fun translate(stmt: PtIfElse) {
        require(stmt.condition.type== DataType.BOOL)

        if(stmt.ifScope.children.singleOrNull() is PtJump) {
            translateIfWithOnlyJump(stmt)
            return
        }

        val afterIfLabel = asmgen.makeLabel("afterif")

        fun translateIfElseBodies(elseBranchInstr: String) {
            if(stmt.hasElse()) {
                // if and else blocks
                val elseLabel = asmgen.makeLabel("else")
                asmgen.out("  $elseBranchInstr  $elseLabel")
                asmgen.translate(stmt.ifScope)
                asmgen.jmp(afterIfLabel, false)
                asmgen.out(elseLabel)
                asmgen.translate(stmt.elseScope)
            } else {
                // no else block
                asmgen.out("  $elseBranchInstr  $afterIfLabel")
                asmgen.translate(stmt.ifScope)
            }
            asmgen.out(afterIfLabel)
        }

        val compareCond = stmt.condition as? PtBinaryExpression
        if(compareCond!=null) {
            if((compareCond.right as? PtNumber)?.number==0.0 && compareCond.operator in arrayOf("==", "!=")) {
                if (compareCond.right.type in ByteDatatypesWithBoolean) {
                    // equality comparison with 0     TODO temporary optimization
                    val elseBranch = if (compareCond.operator == "==") "bne" else "beq"
                    asmgen.assignExpressionToRegister(compareCond.left, RegisterOrPair.A, false)
                    translateIfElseBodies(elseBranch)
                    return
                }
            } else {
                // TODO optimize if X comparison Y general case (also with 0 as a special case)
            }
        }

        val prefixCond = stmt.condition as? PtPrefix
        if(prefixCond?.operator=="not") {
            asmgen.assignExpressionToRegister(prefixCond.value, RegisterOrPair.A, false)
            translateIfElseBodies("bne") // inverted condition, just swap the branches
        } else {
            asmgen.assignExpressionToRegister(stmt.condition, RegisterOrPair.A, false)
            translateIfElseBodies("beq")
        }
    }

    private fun translateIfWithOnlyJump(stmt: PtIfElse) {
        val jump = stmt.ifScope.children.single() as PtJump
        val compareCond = stmt.condition as? PtBinaryExpression

        fun doJumpAndElse(branchInstr: String, falseBranch: String, jump: PtJump) {
            val (asmLabel, indirect) = asmgen.getJumpTarget(jump)
            if(indirect) {
                asmgen.out("""
                $falseBranch  +
                jmp  ($asmLabel)
+           """)
            } else {
                asmgen.out("  $branchInstr  $asmLabel")
            }
            if(stmt.hasElse())
                asmgen.translate(stmt.elseScope)
        }

        if(compareCond!=null) {
            if (compareCond.operator in arrayOf("==", "!=")) {
                val compareNumber = compareCond.right as? PtNumber
                when (compareCond.right.type) {
                    in ByteDatatypesWithBoolean -> {
                        asmgen.assignExpressionToRegister(compareCond.left, RegisterOrPair.A, false)
                        if(compareNumber==null || compareNumber.number!=0.0)
                            compareRegisterAwithByte(compareCond.right)
                        if(compareCond.operator=="==") {
                            // if X==something goto blah
                            doJumpAndElse("beq", "bne", jump)
                            return
                        } else {
                            // if X!=something goto blah
                            doJumpAndElse("bne", "brq", jump)
                            return
                        }
                    }
                    in WordDatatypes -> {
                        asmgen.assignExpressionToRegister(stmt.condition, RegisterOrPair.A, false)
                        doJumpAndElse("bne", "beq", jump)
                        return
                        // TODO: optimize word
//                        assignExpressionToRegister(compareCond.left, RegisterOrPair.AY, false)
//                        compareRegisterAYwithWord(compareCond.operator, compareCond.right, jump)
//                        if(compareCond.operator=="==") {
//                            // if X==something goto blah
//                            doJumpAndElse("beq", "bne", jump)
//                            return
//                        } else {
//                            // if X!=something goto blah
//                            doJumpAndElse("bne", "brq", jump)
//                            return
//                        }
                    }
                    DataType.FLOAT -> {
                        TODO()
                    }
                    else -> {
                        throw AssemblyError("weird dt")
                    }
                }
            }
        }

        asmgen.assignExpressionToRegister(stmt.condition, RegisterOrPair.A, false)
        doJumpAndElse("bne", "beq", jump)
    }

    private fun compareRegisterAwithByte(value: PtExpression) {
        fun cmpViaScratch() {
            if(!value.isSimple()) asmgen.out("  pha")
            asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_REG", value.type)
            if(!value.isSimple()) asmgen.out("  pla")
            asmgen.out("  cmp  P8ZP_SCRATCH_REG")
        }

        // TODO how is  if X==pointeervar[99]  translated? can use cmp indirect indexed

        when(value) {
            is PtArrayIndexer -> {
                val constIndex = value.index.asConstInteger()
                if(constIndex!=null) {
                    val offset = constIndex * program.memsizer.memorySize(value.type)
                    if(offset<256) {
                        asmgen.out("  ldy  #$offset |  cmp  ${asmgen.asmVariableName(value.variable.name)},y")
                        return
                    }
                }
                cmpViaScratch()
            }
            is PtMemoryByte -> {
                val constAddr = value.address.asConstInteger()
                if(constAddr!=null) {
                    asmgen.out("  cmp  ${constAddr.toHex()}")
                } else {
                    cmpViaScratch()
                }
            }
            is PtIdentifier -> {
                asmgen.out("  cmp  ${asmgen.asmVariableName(value.name)}")
            }
            is PtNumber -> {
                if(value.number!=0.0)
                    asmgen.out("  cmp  #${value.number.toInt()}")
            }
            else -> {
                cmpViaScratch()
            }
        }
    }

}