%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {
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
