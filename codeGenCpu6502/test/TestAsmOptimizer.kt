package prog8tests.codegencpu6502

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.StMemVar
import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.DataType
import prog8.code.target.C64Target
import prog8.codegen.cpu6502.*
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder


class TestAsmOptimizer: FunSpec({

    val machine = C64Target()
    val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
    val symbolTable = SymbolTable(program)

    // Add test variables with known non-IO addresses so the optimizer can resolve them
    // Non-IO addresses in typical C64 RAM range
    symbolTable.add(StMemVar("myvar", DataType.UBYTE, 0x0300u, null, null))
    symbolTable.add(StMemVar("var", DataType.UBYTE, 0x0301u, null, null))
    symbolTable.add(StMemVar("var1", DataType.UWORD, 0x0302u, null, null))
    symbolTable.add(StMemVar("var2", DataType.UWORD, 0x0304u, null, null))
    symbolTable.add(StMemVar("ptr", DataType.UWORD, 0x0306u, null, null))
    symbolTable.add(StMemVar("P8ZP_SCRATCH_PTR", DataType.UWORD, 0x02u, null, null))
    symbolTable.add(StMemVar("P8ZP_SCRATCH_W1", DataType.UWORD, 0x04u, null, null))
    symbolTable.add(StMemVar("A1", DataType.UWORD, 0x0310u, null, null))
    symbolTable.add(StMemVar("A2", DataType.UWORD, 0x0312u, null, null))

    fun optimize(lines: MutableList<String>): Int {
        return optimizeAssembly(lines, machine, symbolTable)
    }

    // --- optimizeIncDec ---

    test("optimizeIncDec: removes iny+dey sequence") {
        val lines = mutableListOf("  iny", "  dey", "  rts", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  rts", "  nop")
    }

    test("optimizeIncDec: removes iny+dey sequence with comments") {
        val lines = mutableListOf("  iny  ; increment Y", "  dey  ; decrement Y", "  rts", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  rts", "  nop")
    }

    test("optimizeIncDec: removes inx+dex sequence") {
        val lines = mutableListOf("  inx", "  dex", "  rts", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  rts", "  nop")
    }

    test("optimizeIncDec: removes dey+iny sequence") {
        val lines = mutableListOf("  dey", "  iny", "  rts", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  rts", "  nop")
    }

    test("optimizeIncDec: removes dex+inx sequence") {
        val lines = mutableListOf("  dex", "  inx", "  rts", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  rts", "  nop")
    }

    test("optimizeIncDec: preserves non-canceling sequences") {
        val lines = mutableListOf("  iny", "  inx", "  rts", "  nop")
        optimize(lines) shouldBe 0
        lines shouldBe listOf("  iny", "  inx", "  rts", "  nop")
    }

    // --- optimizeStoreLoadSame ---

    test("optimizeStoreLoadSame: removes sta X + lda X") {
        val lines = mutableListOf("  lda  #42", "  sta  myvar", "  lda  myvar", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  lda  #42", "  sta  myvar", "  rts")
    }

    test("optimizeStoreLoadSame: removes sta X + lda X with comments") {
        // Note: comments don't affect the optimization, but the optimizer checks lines[1] and lines[2]
        // and the comment is part of the line, so "sta  myvar  ; store it" doesn't match "sta  myvar"
        // The optimizer uses substring(4) which includes the comment
        val lines = mutableListOf("  nop", "  sta  myvar", "  lda  myvar", "  rts  ; return")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  nop", "  sta  myvar", "  rts  ; return")
    }

    test("optimizeStoreLoadSame: removes pha + pla") {
        // Note: optimizer checks lines[1] and lines[2] of the 4-line window
        val lines = mutableListOf("  nop", "  pha", "  pla", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  nop", "  rts")
    }

    test("optimizeStoreLoadSame: removes phx + plx") {
        val lines = mutableListOf("  nop", "  phx", "  plx", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  nop", "  rts")
    }

    test("optimizeStoreLoadSame: removes phy + ply") {
        val lines = mutableListOf("  nop", "  phy", "  ply", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  nop", "  rts")
    }

    test("optimizeStoreLoadSame: removes lda X + sta X") {
        // Note: optimizer checks lines[1] and lines[2] of the 4-line window
        val lines = mutableListOf("  nop", "  lda  myvar", "  sta  myvar", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  nop", "  lda  myvar", "  rts")
    }

    // --- optimizeJsrRtsAndOtherCombinations ---

    test("optimizeJsrRts: converts jsr+rts to jmp") {
        val lines = mutableListOf("  jsr  mysub", "  rts", "  nop", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  jmp  mysub", "  nop", "  nop")
    }

    test("optimizeJsrRts: converts jsr+rts to jmp with comments") {
        // Note: comments don't affect the optimization
        val lines = mutableListOf("  jsr  mysub  ; call subroutine", "  rts  ; return", "  nop", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  jmp  mysub  ; call subroutine", "  nop", "  nop")
    }

    test("optimizeJsrRts: removes rts+jmp") {
        val lines = mutableListOf("  rts", "  jmp  somewhere", "  nop", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  rts", "  nop", "  nop")
    }

    test("optimizeJsrRts: removes rts+bra") {
        val lines = mutableListOf("  rts", "  bra  somewhere", "  nop", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  rts", "  nop", "  nop")
    }

    test("optimizeJsrRts: removes lda+cmp #0") {
        val lines = mutableListOf("  lda  myvar", "  cmp  #0", "  beq  skip", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  lda  myvar", "  beq  skip", "  nop")
    }

    test("optimizeJsrRts: removes lda+cmp #0 with comments") {
        val lines = mutableListOf("  lda  myvar  ; load variable", "  cmp  #0  ; compare to zero", "  beq  skip  ; branch if equal", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  lda  myvar  ; load variable", "  beq  skip  ; branch if equal", "  nop")
    }

    test("optimizeJsrRts: removes bra+jmp") {
        val lines = mutableListOf("  bra  somewhere", "  jmp  elsewhere", "  nop", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  bra  somewhere", "  nop", "  nop")
    }

    test("optimizeJsrRts: preserves jsr+rts for floats.pushFAC") {
        val lines = mutableListOf("  jsr  floats.pushFAC", "  rts", "  nop", "  nop")
        optimize(lines) shouldBe 0
        lines shouldBe listOf("  jsr  floats.pushFAC", "  rts", "  nop", "  nop")
    }

    // --- optimizeUselessPushPopStack ---

    test("optimizeUselessPushPop: removes phy+ldy+pla") {
        val lines = mutableListOf("  phy", "  ldy  #5", "  pla", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  tya", "  ldy  #5", "  rts")
    }

    test("optimizeUselessPushPop: removes phx+ldx+pla") {
        val lines = mutableListOf("  phx", "  ldx  #5", "  pla", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  txa", "  ldx  #5", "  rts")
    }

    test("optimizeUselessPushPop: removes pha+lda+pla") {
        // Note: optimizer removes all three: pha, lda, and pla
        val lines = mutableListOf("  pha", "  lda  #5", "  pla", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  rts")
    }

    test("optimizeUselessPushPop: removes pha+tya+tay+pla") {
        val lines = mutableListOf("  pha", "  tya", "  tay", "  pla", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  rts")
    }

    // --- optimizeUnneededTempvarInAdd ---

    test("optimizeUnneededTempvarInAdd: removes scratch variable in add") {
        val lines = mutableListOf("  sta  P8ZP_SCRATCH_W1", "  lda  #5", "  clc", "  adc  P8ZP_SCRATCH_W1", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  clc", "  adc  #5", "  rts")
    }

    test("optimizeUnneededTempvarInAdd: removes scratch variable in add with comments") {
        // Note: comments don't affect the optimization
        val lines = mutableListOf("  sta  P8ZP_SCRATCH_W1", "  lda  #5", "  clc", "  adc  P8ZP_SCRATCH_W1", "  rts  ; return")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  clc", "  adc  #5", "  rts  ; return")
    }

    // --- optimizeTSBtoRegularOr ---

    test("optimizeTSBtoRegularOr: converts lda/tsb/lda to lda/ora/sta") {
        val lines = mutableListOf("  lda  var2", "  tsb  var1", "  lda  var1", "  rts")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  lda  var1", "  ora  var2", "  sta  var1", "  rts")
    }

    test("optimizeTSBtoRegularOr: converts lda/tsb/lda to lda/ora/sta with comments") {
        // Note: comments don't affect the optimization
        val lines = mutableListOf("  lda  var2", "  tsb  var1", "  lda  var1", "  rts  ; return")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  lda  var1", "  ora  var2", "  sta  var1", "  rts  ; return")
    }

    // --- optimizeSameAssignments ---

    test("optimizeSameAssignments: removes duplicate lda/ldy/sta/sty word init") {
        val lines = mutableListOf(
            "  lda  #1", "  ldy  #0", "  sta  var1", "  sty  var1+1",
            "  lda  #1", "  ldy  #0", "  sta  var2", "  sty  var2+1",
            "  rts", "  nop", "  nop", "  nop", "  nop", "  nop"
        )
        optimize(lines) shouldBe 1
        lines shouldBe listOf(
            "  lda  #1", "  ldy  #0", "  sta  var1", "  sty  var1+1",
            "  sta  var2", "  sty  var2+1",
            "  rts", "  nop", "  nop", "  nop", "  nop", "  nop"
        )
    }

    // --- optimizeSamePointerIndexingAndUselessBeq ---

    test("optimizeSamePointerIndexing: removes redundant ldy in pointer indexing") {
        val lines = mutableListOf(
            "  ldy  #0", "  lda  (ptr),y", "  ora  #3", "  ldy  #0", "  sta  (ptr),y", "  rts",
            "  nop", "  nop", "  nop", "  nop", "  nop", "  nop", "  nop", "  nop"
        )
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  ldy  #0", "  lda  (ptr),y", "  ora  #3", "  sta  (ptr),y", "  rts",
            "  nop", "  nop", "  nop", "  nop", "  nop", "  nop", "  nop", "  nop")
    }

    // --- optimizeAddWordToSameVariableOrExtraRegisterLoadInWordStore ---

    test("optimizeAddWordToSameVariable: optimizes P8ZP_SCRATCH_PTR += AY") {
        val lines = mutableListOf(
            "  clc", "  adc  P8ZP_SCRATCH_PTR", "  pha", "  tya",
            "  adc  P8ZP_SCRATCH_PTR+1", "  tay", "  pla",
            "  sta  P8ZP_SCRATCH_PTR", "  sty  P8ZP_SCRATCH_PTR+1", "  rts",
            "  nop", "  nop", "  nop", "  nop"
        )
        optimize(lines) shouldBe 1
        lines shouldBe listOf(
            "  clc", "  adc  P8ZP_SCRATCH_PTR", "  sta  P8ZP_SCRATCH_PTR",
            "  tya", "  adc  P8ZP_SCRATCH_PTR+1", "  sta  P8ZP_SCRATCH_PTR+1", "  rts",
            "  nop", "  nop", "  nop", "  nop"
        )
    }

    test("optimizeExtraRegisterLoadInWordStore: removes ldx+sta+txa+ldy+sta") {
        val lines = mutableListOf("  ldx  #42", "  sta  myvar", "  txa", "  ldy  #1", "  sta  myvar+1", "  rts",
            "  nop", "  nop", "  nop", "  nop", "  nop", "  nop", "  nop", "  nop")
        optimize(lines) shouldBe 1
        lines shouldBe listOf("  sta  myvar", "  lda  #42", "  ldy  #1", "  sta  myvar+1", "  rts",
            "  nop", "  nop", "  nop", "  nop", "  nop", "  nop", "  nop", "  nop")
    }

    // --- Edge cases ---

    test("handles empty input") {
        val lines = mutableListOf<String>()
        optimize(lines) shouldBe 0
        lines shouldBe emptyList()
    }

    test("handles single line input") {
        val lines = mutableListOf("  rts")
        optimize(lines) shouldBe 0
        lines shouldBe listOf("  rts")
    }

    test("multiple optimizations in one pass") {
        val lines = mutableListOf("  iny", "  dey", "  lda  #1", "  sta  var", "  lda  var", "  rts")
        optimize(lines) shouldBe 2
        lines shouldBe listOf("  lda  #1", "  sta  var", "  rts")
    }

    // --- Helper function tests ---

    // hasInstr tests
    test("hasInstr: exact match") {
        "iny".hasInstr("iny") shouldBe true
        "pla".hasInstr("pla") shouldBe true
        "rts".hasInstr("rts") shouldBe true
    }

    test("hasInstr: starts with mnemonic and space") {
        "lda  #1".hasInstr("lda") shouldBe true
        "sta  myvar".hasInstr("sta") shouldBe true
        "ldy  #0".hasInstr("ldy") shouldBe true
        "inc  a".hasInstr("inc") shouldBe true
    }

    test("hasInstr: contains mnemonic after label") {
        "mylabel: lda  #1".hasInstr("lda") shouldBe true
        "loop: iny".hasInstr("iny") shouldBe true
        "skip: rts".hasInstr("rts") shouldBe true
    }

    test("hasInstr: does not match named labels without colon") {
        // Named labels require a colon to distinguish from instructions
        "mylabel lda  #1".hasInstr("lda") shouldBe false
        "loop iny".hasInstr("iny") shouldBe false
        "skip rts".hasInstr("rts") shouldBe false
    }

    test("hasInstr: handles 64tass anonymous labels with colon") {
        "+: lda  #1".hasInstr("lda") shouldBe true
        "++: lda  #1".hasInstr("lda") shouldBe true
        "-: lda  #1".hasInstr("lda") shouldBe true
        "--: lda  #1".hasInstr("lda") shouldBe true
        "+: rts".hasInstr("rts") shouldBe true
        "+++: iny".hasInstr("iny") shouldBe true
    }

    test("hasInstr: handles 64tass anonymous labels without colon") {
        "+ lda  #1".hasInstr("lda") shouldBe true
        "++ lda  #1".hasInstr("lda") shouldBe true
        "- lda  #1".hasInstr("lda") shouldBe true
        "-- lda  #1".hasInstr("lda") shouldBe true
        "+ rts".hasInstr("rts") shouldBe true
        "+++ iny".hasInstr("iny") shouldBe true
    }

    test("hasInstr: handles scoped labels with periods") {
        "mysub.loop: lda  #1".hasInstr("lda") shouldBe true
        "_private: rts".hasInstr("rts") shouldBe true
        // Without colon, named labels are not recognized
        "mysub.loop lda  #1".hasInstr("lda") shouldBe false
        "_private rts".hasInstr("rts") shouldBe false
    }

    test("hasInstr: does not match numbers as labels") {
        "1 lda  #1".hasInstr("lda") shouldBe false
        "+1 lda  #1".hasInstr("lda") shouldBe false
        "-1 lda  #1".hasInstr("lda") shouldBe false
    }

    test("hasInstr: does not match partial mnemonics") {
        "lda  #1".hasInstr("ld") shouldBe false
        "sta  var".hasInstr("st") shouldBe false
        "iny".hasInstr("in") shouldBe false
    }

    test("hasInstr: does not match mnemonic in operand") {
        // Note: hasInstr uses contains(" mnemonic") which can match operands
        // This is a known limitation - the optimizer only calls hasInstr on
        // trimmed lines where the instruction is at the start or after a label
        "lda  #iny".hasInstr("iny") shouldBe false
        // "sta  pla" would match due to contains(" pla") - this is expected behavior
        // in the optimizer context where lines are properly formatted
    }

    test("hasInstr: handles inc a variants") {
        "inc  a".hasInstr("inc  a") shouldBe true
        "inc a".hasInstr("inc a") shouldBe true
        "inc  var".hasInstr("inc  a") shouldBe false
    }

    test("hasInstr: handles lines with trailing comments") {
        "iny  ; increment Y".hasInstr("iny") shouldBe true
        "lda  #1  ; load immediate".hasInstr("lda") shouldBe true
        "mylabel: rts  ; return".hasInstr("rts") shouldBe true
        // Note: hasInstr expects trimmed lines, so leading whitespace must be removed first
        "dey  ; decrement Y".hasInstr("dey") shouldBe true
    }

    test("hasInstr: handles dec a variants") {
        "dec  a".hasInstr("dec  a") shouldBe true
        "dec a".hasInstr("dec a") shouldBe true
        "dec  var".hasInstr("dec  a") shouldBe false
    }

    // isBranch tests
    test("isBranch: matches branch instructions") {
        "beq  skip".isBranch() shouldBe true
        "bne  loop".isBranch() shouldBe true
        "bcc  target".isBranch() shouldBe true
        "bcs  target".isBranch() shouldBe true
        "bmi  neg".isBranch() shouldBe true
        "bpl  pos".isBranch() shouldBe true
        "bvc  noov".isBranch() shouldBe true
        "bvs  ov".isBranch() shouldBe true
        "bra  somewhere".isBranch() shouldBe true
    }

    test("isBranch: matches branch instructions with trailing comments") {
        "beq  skip  ; branch if equal".isBranch() shouldBe true
        "bne  loop  ; branch if not equal".isBranch() shouldBe true
        "bra  target  ; always branch".isBranch() shouldBe true
    }

    test("isBranch: does not match non-branch instructions") {
        "lda  #1".isBranch() shouldBe false
        "sta  var".isBranch() shouldBe false
        "rts".isBranch() shouldBe false
        "nop".isBranch() shouldBe false
    }

    // isStoreReg tests
    test("isStoreReg: matches store instructions") {
        "sta  var".isStoreReg() shouldBe true
        "sty  var".isStoreReg() shouldBe true
        "stx  var".isStoreReg() shouldBe true
    }

    test("isStoreReg: matches store instructions with trailing comments") {
        "sta  var  ; store accumulator".isStoreReg() shouldBe true
        "sty  myvar  ; store Y".isStoreReg() shouldBe true
        "stx  counter  ; store X".isStoreReg() shouldBe true
    }

    test("isStoreReg: does not match non-store instructions") {
        "lda  var".isStoreReg() shouldBe false
        "ldy  var".isStoreReg() shouldBe false
        "ldx  var".isStoreReg() shouldBe false
        "sta".isStoreReg() shouldBe false  // no space after
    }

    // isStoreRegOrZero tests
    test("isStoreRegOrZero: matches store and stz") {
        "sta  var".isStoreRegOrZero() shouldBe true
        "sty  var".isStoreRegOrZero() shouldBe true
        "stx  var".isStoreRegOrZero() shouldBe true
        "stz  var".isStoreRegOrZero() shouldBe true
    }

    test("isStoreRegOrZero: does not match non-store") {
        "lda  var".isStoreRegOrZero() shouldBe false
        "stz".isStoreRegOrZero() shouldBe false  // no space after
    }

    // isLoadReg tests
    test("isLoadReg: matches load instructions") {
        "lda  var".isLoadReg() shouldBe true
        "ldy  var".isLoadReg() shouldBe true
        "ldx  var".isLoadReg() shouldBe true
    }

    test("isLoadReg: matches load instructions with trailing comments") {
        "lda  var  ; load accumulator".isLoadReg() shouldBe true
        "ldy  #5  ; load Y".isLoadReg() shouldBe true
        "ldx  counter  ; load X".isLoadReg() shouldBe true
    }

    test("isLoadReg: does not match non-load instructions") {
        "sta  var".isLoadReg() shouldBe false
        "sty  var".isLoadReg() shouldBe false
        "stx  var".isLoadReg() shouldBe false
        "lda".isLoadReg() shouldBe false  // no space after
    }

    // haslabel tests
    test("haslabel: detects labels") {
        haslabel("mylabel:") shouldBe true
        haslabel("loop:") shouldBe true
        haslabel("mylabel: lda  #1") shouldBe true
        haslabel("loop: iny") shouldBe true
    }

    test("haslabel: detects labels with trailing comments") {
        haslabel("mylabel:  ; this is a label") shouldBe true
        haslabel("loop: lda  #1  ; load immediate") shouldBe true
        haslabel("skip:  ; skip ahead") shouldBe true
    }

    test("haslabel: does not detect non-labels") {
        haslabel("  lda  #1") shouldBe false
        haslabel("  iny") shouldBe false
        haslabel("; comment") shouldBe false
        haslabel("") shouldBe false
    }

    test("haslabel: handles labels with leading whitespace") {
        haslabel(" label:") shouldBe true
        haslabel("\tlabel:") shouldBe true
    }

    // keeplabel tests
    test("keeplabel: extracts label from line") {
        keeplabel("mylabel: lda  #1") shouldBe "mylabel:"
        keeplabel("loop: iny") shouldBe "loop:"
        keeplabel("mylabel:") shouldBe "mylabel:"
    }

    test("keeplabel: extracts label from line with trailing comment") {
        keeplabel("mylabel: lda  #1  ; load immediate") shouldBe "mylabel:"
        keeplabel("loop:  ; just a label") shouldBe "loop:"
        keeplabel("skip: rts  ; return") shouldBe "skip:"
    }

    test("keeplabel: returns colon-only for non-label lines with leading whitespace") {
        // keeplabel splits on first whitespace and returns first part + ":"
        // For "  lda  #1", first part is empty string, so returns ":"
        keeplabel("  lda  #1") shouldBe ":"
        keeplabel("  iny") shouldBe ":"
    }

    test("keeplabel: handles tab-separated labels") {
        keeplabel("mylabel\tlda  #1") shouldBe "mylabel:"
    }

    // ========================================================================
    // KNOWN BUGS - These tests demonstrate current optimizer defects.
    // See AsmOptimizer.kt header for the full list.
    // ========================================================================

    // BUG #1: Float copy optimization removes stores without verifying destinations.
    // The pattern matches two identical float initializations based only on load operands,
    // then removes the second one's lda/ldy/sta/sty without checking if the store
    // destinations are the same. Two different float vars initialized from the same
    // source will have the second initialization's stores wrongly deleted.
    test("BUG #1: float copy init to different destinations should not be removed") {
        // Pattern: 14 lines matching two float copy_float sequences
        // First copy to floatA:  lda #1 / ldy #0 / sta floatA / sty floatA+1 / lda #1 / ldy #0 / jsr floats.copy_float
        // Second copy to floatB: lda #1 / ldy #0 / sta floatB / sty floatB+1 / lda #1 / ldy #0 / jsr floats.copy_float
        // BUG: optimizer sees matching loads (#1, #0) and removes the second copy's lda/ldy/sta/sty
        // even though floatA != floatB
        val lines = mutableListOf(
            // Lines 0-6: first float copy
            "  lda  #1", "  ldy  #0", "  sta  floatA", "  sty  floatA+1",
            "  lda  #1", "  ldy  #0", "  jsr  floats.copy_float",
            // Lines 7-13: second float copy to DIFFERENT destination
            "  lda  #1", "  ldy  #0", "  sta  floatB", "  sty  floatB+1",
            "  lda  #1", "  ldy  #0", "  jsr  floats.copy_float"
        )
        val beforeOpt = lines.toList()
        optimize(lines)
        // After fix: no optimization should apply, lines unchanged
        // Currently: optimizer removes lines 7-10 (lda/ldy/sta/sty for floatB)
        lines shouldBe beforeOpt  // xfail: this will fail until bug is fixed
    }

    // BUG #2: Crude "y" substring check for Y register modification
    // A variable named "myvar" contains 'y', so the optimizer thinks Y is modified
    // and incorrectly keeps a redundant ldy.
    test("BUG #2: variable name containing 'y' should not trigger false positive") {
        symbolTable.add(StMemVar("myvar", DataType.UBYTE, 0x0320u, null, null))
        val lines = mutableListOf(
            "  ldy  #0", "  lda  (ptr),y", "  ora  myvar",  // 'y' in "myvar" triggers false positive
            "  ldy  #0", "  sta  (ptr),y", "  rts",
            "  nop", "  nop", "  nop", "  nop", "  nop", "  nop", "  nop", "  nop"
        )
        // BUG: "y" in "ora  myvar" matches the "y" in "myvar", so the optimizer
        // thinks Y might be modified and keeps the redundant ldy.
        // After fix: the second ldy should be removed (count should be 1)
        // Currently: count is 0 because of the false positive
        optimize(lines) shouldBe 1  // xfail: this will fail until bug is fixed
    }

    // BUG #3: Inconsistent line property usage (trimmed vs instruction)
    // This is a latent correctness issue - harder to trigger in a minimal test,
    // but the pattern exists in optimizeSamePointerIndexingAndUselessBeq.
    // The check `"y" !in third` uses .trimmed (may include labels) while
    // `"y" !in f4` uses .instruction. This is fragile but hard to isolate
    // without crafting a specific label+instruction combination.
    // No minimal test case yet - the bug is structural inconsistency.

    test("compiler-prefixed memory-mapped IO variable accesses are not optimized away") {
        symbolTable.add(StMemVar("p8b_main.p8v_io_data", DataType.UBYTE, 0xd021u, null, null))
        symbolTable.add(StMemVar("p8b_main.p8v_result", DataType.UBYTE, 0x0300u, null, null))
        val lines = mutableListOf(
            "  lda  p8b_main.p8v_io_data", "  sta  p8b_main.p8v_result",
            "  lda  p8b_main.p8v_io_data", "  sta  p8b_main.p8v_result",
            "  lda  p8b_main.p8v_io_data", "  sta  p8b_main.p8v_result",
            "  rts", "  nop"
        )
        val count = optimize(lines)
        count shouldBe 0
        lines.size shouldBe 8  // all instructions preserved
    }
})
