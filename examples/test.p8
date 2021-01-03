%import test_stack
%import textio
%zeropage full
%option no_sysinit

main {

    sub start () {
        uword current_time

        repeat 5 {
            current_time = c64.RDTIM16()
            txt.print_uw(current_time)
            txt.chrout('\n')
            repeat 60000 {
                current_time++
            }
        }


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
