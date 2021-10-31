%import textio
%zeropage dontuse

main {

    sub start() {

        ubyte xx = 100
        uword qq =$1234+xx  ; TODO FIX THAT THIS USES STACK

        uword ww
        ww=$1234+xx

;        ubyte cv
;        sys.memset($1000+xx, 10, 255)       ; TODO uses stack eval now to precalc parameters
;
;        xx = xx & %0001     ; doesn't use stack...      because it uses AugmentableAssignmentAsmGen
;        ;yy = xx & %0001     ; doesn't use stack...      because it uses AugmentableAssignmentAsmGen
;
;        ;ubyte yy = xx & %0001       ; TODO uses stack eval....
;        if xx & %0001 {     ; TODO why does this use stack?   because it uses asmgen.assignExpressionToRegister   eventually line 253 in AssignmentAsmGen   no augmentable-assignment.
;            xx--
;        }

;        if xx+1 {             ; TODO why does this use stack? see above
;            xx++
;        }
;
;        if xx {             ; doesn't use stack...
;            xx++
;        }
;
;        xx = xx+1           ; doesn't use stack...
;
;        if 8<xx {
;        }
;
;        do {
;            xx++
;        } until xx+1
;
;        while xx+1 {
;            xx++
;        }
    }
}
