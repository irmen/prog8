%import test_stack
%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {
        uword current_time
        uword secs
        const uword test_value = 1261

        current_time = test_value
        secs = current_time / 60
        current_time = (current_time - secs*60)*1000/60
        txt.print_uw(secs)
        txt.chrout('.')
        if current_time<10
            txt.chrout('0')
        if current_time<100
            txt.chrout('0')
        txt.print_uw(current_time)
        txt.chrout('\n')

        current_time = test_value
        secs = current_time / 60
        current_time = current_time - secs*60
        current_time *= 1000
        current_time /= 60
        txt.print_uw(secs)
        txt.chrout('.')
        if current_time<10
            txt.chrout('0')
        if current_time<100
            txt.chrout('0')
        txt.print_uw(current_time)
        txt.chrout('\n')


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
