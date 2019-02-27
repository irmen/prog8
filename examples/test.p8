%import c64utils
%zeropage basicsafe

~ main {

    ; @todo test memset/memcopy  (there's a bug in memcopy?)

    ; @todo see problem in looplabelproblem.p8

    ; @todo add docs for '@zp' tag in variable datatype declarations (including forloop loopvars)
    ; @todo gradle fatJar should include the antlr runtime jar

    ;  uword x = sin8u(bb) as uword + 50     ; @todo fix "cannot assign word to uword"
    ;  uword ypos=4;  ypos += 5000                ; @todo fix "cannot assign word to uword"


    sub start() {

        uword ypos=4

        byte bb=44
        byte bb2
        word ww=4444
        word ww2

        bb2 = bb*55
        ww2 = ww*55

        uword x = sin8u(bb) as uword + 50     ; @todo fix "cannot assign word to uword"
        ;ypos += 5000                ; @todo fix "cannot assign word to uword"

;
;        memset($0400+(ypos+0)*40, 40, 1)
;        memset($0400+(ypos+1)*40, 40, 2)
;        memset($0400+(ypos+2)*40, 40, 3)
;        memset($0400+(ypos+3)*40, 40, 4)

        ;memsetw($0400+(ypos+1)*40, 20, $4455)
        ;memsetw($0400+(ypos+3)*40, 20, $4455)

    }
}
