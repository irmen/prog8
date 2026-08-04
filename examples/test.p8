%import textio

; Exercises the "constant fast path for operatorGreaterThan/operatorLessThan"
; optimization. operatorEquals has a constant fast path (ExpressionGen.kt:1329:
; detects binExpr.right.asConstValue() and emits CMPI #imm directly). The
; > and < operators do NOT have a similar fast path -- they always emit
; a register-register compare, even when the right operand is a constant
; literal.
;
; For b = x > 0 (where x is a variable), the current code emits:
;   LOAD #0, d1              ; load constant into a register
;   CMP d1, d0               ; register-register compare
;   BGT labelTrue
;   LOAD #0, resultReg
;   JUMP done
;   LOAD #1, resultReg
;
; The fast path would emit just:
;   TST d0                   ; (or CMPI #0, d0 on 6502)
;   BGT labelTrue
;   LOAD #0, resultReg
;   JUMP done
;   LOAD #1, resultReg
;
; Saves the LOAD #0 and CMP instructions (2 registers of regfile, 2 instr
; slots per occurrence). Same applies to ==, !=, <, <=, >=, s>, s>=, s<, s<=.
;
;   prog8c -target qemu68k -out /tmp/opencode examples/test.p8
;   grep -E 'BGT|BLT|BGE|BLE' /tmp/opencode/test.p8ir
;   prog8c -target qemu68k -emu examples/test.p8

main {
    sub start() {
        ubyte @shared x = 5
        word  @shared y = -100
        uword @shared z = 30000

        ; --- operatorEquals (already has fast path) ---------------------
        ; b = x == 0  ->  CMPI #0, rX; BSTNE labelTrue; ...
        bool eq_zero  = x == 0
        ; b = y == -1  ->  CMPI #-1, rY; BSTNE labelTrue; ...
        bool eq_neg   = y == -1
        ; b = z == 1000  ->  CMPI #1000, rZ; BSTNE labelTrue; ...
        bool eq_big   = z == 1000

        ; --- operatorGreaterThan (no fast path) -------------------------
        ; b = x > 0   ->  LOAD #0, d1; CMP d1, d0; BGT labelTrue; ...
        bool gt_zero  = x > 0
        ; b = y > 0   ->  LOAD #0, d1; CMP d1, d0; BGT labelTrue; ...
        bool gt_y     = y > 0
        ; b = z > 100 ->  LOAD #100, d1; CMP d1, d0; BGT labelTrue; ...
        bool gt_100   = z > 100

        ; --- operatorLessThan (no fast path) ----------------------------
        ; b = x < 10  ->  LOAD #10, d1; CMP d1, d0; BLT labelTrue; ...
        bool lt_10    = x < 10
        ; b = y < 0   ->  LOAD #0, d1; CMP d1, d0; BLT labelTrue; ...
        bool lt_zero  = y < 0
        ; b = z < 50000 ->  LOAD #50000, d1; CMP d1, d0; BLT labelTrue; ...
        bool lt_big   = z < 50000

        ; --- >=, <= (also no fast path) --------------------------------
        bool ge_5     = x >= 5
        bool le_5     = x <= 5

        ; --- print results ---------------------------------------------
        txt.print_ub(x)         ; 5
        txt.nl()
        txt.print_w(y)          ; -100
        txt.nl()
        txt.print_uw(z)         ; 30000
        txt.nl()
        txt.print_ub(eq_zero as ubyte)   ; 0
        txt.nl()
        txt.print_ub(eq_neg as ubyte)    ; 0
        txt.nl()
        txt.print_ub(eq_big as ubyte)    ; 0
        txt.nl()
        txt.print_ub(gt_zero as ubyte)   ; 1
        txt.nl()
        txt.print_ub(gt_y as ubyte)      ; 0
        txt.nl()
        txt.print_ub(gt_100 as ubyte)     ; 1
        txt.nl()
        txt.print_ub(lt_10 as ubyte)     ; 1
        txt.nl()
        txt.print_ub(lt_zero as ubyte)    ; 1
        txt.nl()
        txt.print_ub(lt_big as ubyte)     ; 1
        txt.nl()
        txt.print_ub(ge_5 as ubyte)       ; 1
        txt.nl()
        txt.print_ub(le_5 as ubyte)       ; 1
        txt.nl()
    }
}
