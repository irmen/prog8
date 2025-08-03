package prog8.codegen.cpu6502.assignment

import prog8.code.ast.IPtSubroutine
import prog8.code.ast.PtIdentifier
import prog8.code.ast.PtPointerDeref
import prog8.code.core.*
import prog8.codegen.cpu6502.AsmGen6502Internal
import prog8.codegen.cpu6502.VariableAllocator


internal class PtrTarget(target: AsmAssignTarget) {
    val dt = target.datatype
    val pointer = target.pointer!!
    val scope = target.scope
    val position = target.position
}


internal class PointerAssignmentsGen(private val asmgen: AsmGen6502Internal, private val allocator: VariableAllocator) {
    internal fun assignAddressOf(
        target: PtrTarget,
        sourceName: String,
        msb: Boolean,
        arrayDt: DataType?,
        arrayIndexExpr: Nothing?
    ) {
        TODO("assign address of to pointer deref ${target.position}")
    }

    internal fun assignWordVar(target: PtrTarget, sourceName: String, sourceDt: DataType) {
        TODO("assign word variable to pointer deref ${target.position}")
    }

    internal fun assignFAC1(target: PtrTarget) {
        TODO("assign FAC1 float to pointer deref ${target.position}")
    }

    internal fun assignFloatAY(target: PtrTarget) {
        TODO("assign float from AY to pointer deref ${target.position}")
    }

    internal fun assignFloatVar(target: PtrTarget, sourceName: String) {
        TODO("assign float variable to pointer deref ${target.position}")
    }

    internal fun assignByteVar(target: PtrTarget, sourceName: String) {
        TODO("assign byte variable to pointer deref ${target.position}")
    }

    internal fun assignByteReg(target: PtrTarget, register: CpuRegister, signed: Boolean, extendWord: Boolean) {
        TODO("assign register byte to pointer deref ${target.position}")
    }

    internal fun assignWordRegister(target: PtrTarget, regs: RegisterOrPair) {
        TODO("assign register pair word to pointer deref ${target.position}")
    }

    internal fun assignWord(target: PtrTarget, word: Int) {
        val zpPtrVar = deref(target.pointer)
        storeIndirectWord(word, zpPtrVar)
    }

    internal fun assignByte(target: PtrTarget, byte: Int) {
        val zpPtrVar = deref(target.pointer)
        storeIndirectByte(byte, zpPtrVar)
    }

    internal fun assignFloat(target: PtrTarget, float: Double) {
        TODO("assign const float $float to pointer deref ${target.position}")
    }

    internal fun assignByteMemory(target: PtrTarget, address: UInt) {
        TODO("assign memory byte to pointer deref ${target.position}")
    }

    internal fun assignByteMemory(target: PtrTarget, identifier: PtIdentifier) {
        TODO("assign memory byte to pointer deref ${target.position}")
    }

    internal fun inplaceByteInvert(target: PtrTarget) {
        TODO("inplace byte invert pointer deref ${target.position}")
    }

    internal fun inplaceWordInvert(target: PtrTarget) {
        TODO("inplace word invert pointer deref ${target.position}")
    }

    internal fun inplaceByteNegate(target: PtrTarget, ignoreDatatype: Boolean, scope: IPtSubroutine?) {
        TODO("inplace byte negate to pointer deref ${target.position}")
    }

    internal fun inplaceWordNegate(target: PtrTarget, ignoreDatatype: Boolean, scope: IPtSubroutine?) {
        TODO("inplace word negate pointer deref ${target.position}")
    }



    private fun deref(pointer: PtPointerDeref): String {
        // walk the pointer deref chain and leaves the final pointer value in a ZP var
        // this will often be the temp var P8ZP_SCRATCH_W1 but can also be the original pointer variable if it is already in zeropage
        if(pointer.chain.isEmpty()) {
            // TODO: do we have to look at derefLast ?

            if(allocator.isZpVar(pointer.startpointer.name))
                return pointer.startpointer.name
            else {
                // have to copy it to temp zp var
                asmgen.assignExpressionToVariable(pointer.startpointer, "P8ZP_SCRATCH_W1", DataType.UWORD)
                return "P8ZP_SCRATCH_W1"
            }
        }

        TODO("deref pointer chain ${pointer.position}")

        // TODO: do we have to look at derefLast ?

    }

    internal fun assignPointerDerefExpression(target: AsmAssignTarget, value: PtPointerDeref) {
        val zpPtrVar = deref(value)
        if(value.type.isByteOrBool) {
            loadIndirectByte(zpPtrVar)
            asmgen.assignRegister(RegisterOrPair.A, target)
        }
        else if(value.type.isWord) {
            loadIndirectWord(zpPtrVar)
            asmgen.assignRegister(RegisterOrPair.AY, target)
        }
        else if(value.type.isFloat)
            TODO("load float")
        else if(value.type.isLong)
            TODO("load long")
        else
            throw AssemblyError("weird dt ${value.type} in pointer deref assignment ${target.position}")
    }

    private fun loadIndirectByte(zpPtrVar: String) {
        // loads byte pointed to by the ptrvar into A
        if(asmgen.isTargetCpu(CpuType.CPU65C02))
            asmgen.out("  lda  ($zpPtrVar)")
        else
            asmgen.out("  ldy  #0 |  lda  ($zpPtrVar),y")
    }

    private fun loadIndirectWord(zpPtrVar: String) {
        // loads word pointed to by the ptr var into AY
        asmgen.out("""
            ldy  #0
            lda  ($zpPtrVar),y
            tax
            iny
            lda  ($zpPtrVar),y
            tay
            txa""")
    }

    private fun storeIndirectByte(byte: Int, zpPtrVar: String) {
        if(asmgen.isTargetCpu(CpuType.CPU65C02)) {
            asmgen.out("  lda  #$byte |  sta  ($zpPtrVar)")
        } else {
            if (byte == 0) {
                asmgen.out("  lda  #0 |  tay |  sta  ($zpPtrVar),y")
            } else {
                asmgen.out("  lda  #$byte |  ldy  #0 |  sta  ($zpPtrVar),y")
            }
        }
    }

    private fun storeIndirectWord(word: Int, zpPtrVar: String) {
        if(word==0) {
            asmgen.out("""
                lda  #0
                tay
                sta  ($zpPtrVar),y
                iny
                sta  ($zpPtrVar),y""")
        } else {
            asmgen.out("""
                lda  #<$word
                ldy  #0
                sta  ($zpPtrVar),y
                lda  #>$word
                iny
                sta  ($zpPtrVar),y""")
        }
    }

}
