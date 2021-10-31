%import textio
%zeropage dontuse

; TODO why are these bigger now than before the var-initializer optimization:
;    wizzine
;    wormfood
;    cube3d-float (THIS ONE IS A LOT BIGGER!!)
;    cube3d-sprites
;    textelite
;    etc.


main {

    ubyte globalvar1
    ubyte globalvar2 = 99

    sub start() {

        uword xx0

        ubyte xx = 100
        uword qq =$1234+xx

        uword ww
        ww=$1234+xx


;        ubyte cv
;        sys.memset($1000+xx, 10, 255)       ; TODO uses stack eval now to precalc parameters

        ;xx = xx & %0001     ; doesn't use stack...      because it uses AugmentableAssignmentAsmGen
        ;yy = xx & %0001     ; doesn't use stack...      because it uses AugmentableAssignmentAsmGen

        ubyte yy = xx & %0001       ; TODO uses stack eval....

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
