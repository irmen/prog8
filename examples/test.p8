%import textio
%import diskio
%import floats
%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {
    sub start () {

        str s1 = "1234567890"
        str s2 = "aapje"

        txt.print_ub(strcopy(s2, s1))
        txt.chrout('\n')
        txt.print_ub(strcopy(s2, s1)+1)
        txt.chrout('\n')
        txt.print_ub(strlen(s2))
        txt.chrout('\n')
        txt.print_ub(strlen(s1))
        txt.chrout('\n')

;        uword xx
;
;        foo(2**xx)     ; TODO arg is zero if x=8, in the function. Param type uword. fix that . also check bit shift
;        foo(1<<xx)     ; TODO fix crash
;        foo($0001<<xx)     ; TODO fix crash
;        foo($0001>>xx)     ; TODO fix crash
;
;
;        xx = $0001<<xx      ; TODO make math.shift_left_w
;        xx = $0001>>xx      ; TODO make math.shift_right_(u)w
;        uword scanline_data_ptr= $6000
;        uword pixptr = xx/8 + scanline_data_ptr      ; TODO why is this code so much larger than the following line:
;        uword pixptr2 = scanline_data_ptr + xx/8


        test_stack.test()
    }

    sub foo(uword argje) {
        txt.print_uwhex(argje, 1)
        txt.chrout('\n')
    }

}
