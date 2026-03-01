%import textio
%zeropage basicsafe
%option no_sysinit

; TODO fix:
; long lv
; lv--


main {
    ; Test the routine
    sub start() {
        long counter
        long lv
        for lv in 4000 to 94000-1 {
            counter++
        }
        for lv in 70000-1 downto 0 {
            counter++
        }
        for lv in 94000-1 downto 4000 {
            counter++
        }

; TODO implement in 6502 codegen:
;        for lv in 4000 to 94000-1 step 10 {
;            counter++
;        }
;        for lv in 70000-1 downto 0 step -10 {
;            counter++
;        }
;        for lv in 94000-1 downto 4000 step -10 {
;            counter++
;        }

        txt.print_l(counter)
        txt.nl()
    }
}

