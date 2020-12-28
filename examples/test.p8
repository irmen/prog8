%import test_stack
%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {


    sub start () {

        ; differences between:
;            @(pp) = cnt as ubyte
;            @(pp) = lsb(cnt)
;            @(pp) = msb(cnt)
;       repeat w as ubyte  /   repeat lsb(w)

; stack based evaluation for this function call even when it's inlined:
;            gfx2.next_pixel((cnt as ubyte) + 30)


        test_stack.test()

    }


}
