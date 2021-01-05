%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {
        uword mem

        mem = 5*mem*c64.MEMTOP(0,1)
        txt.print_uwhex(mem, 1)

        mem = 5*mem*c64.CHRIN()
        txt.print_uwhex(mem,1)

        test_stack.test()



;        found = strfind("irmen de jong", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfind(" irmen-de-jong", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfind("irmen-de-jong ", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfind("irmen-de-jong", ' ')
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')

;        found = strfinds("irmen de jong", "de")
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfinds("irmen de jong", "irmen")
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfinds("irmen de jong", "jong")
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
;        found = strfinds("irmen de jong", "de456")
;        txt.print_uwhex(found, 1)
;        txt.chrout('\n')
    }
}
