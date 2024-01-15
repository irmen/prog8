%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
;        mem()
;        bytes()
        words()
    }

    sub mem() {
        @($2000) = $7a
        rol(@($2000))
        txt.print_ubbin(@($2000), true)
        txt.nl()
        rol2(@($2000))
        txt.print_ubbin(@($2000), true)
        txt.nl()
        ror(@($2000))
        txt.print_ubbin(@($2000), true)
        txt.nl()
        ror2(@($2000))
        txt.print_ubbin(@($2000), true)
        txt.nl()
        txt.nl()
    }

    sub bytes() {
        ubyte[]  wa = [$1a, $2b, $3c]

        txt.print_ub(all(wa))
        txt.spc()
        txt.print_ub(any(wa))
        txt.nl()

        txt.print_ubbin(wa[2], true)
        txt.nl()
        rol(wa[2])
        txt.print_ubbin(wa[2], true)
        txt.nl()
        rol2(wa[2])
        txt.print_ubbin(wa[2], true)
        txt.nl()
        ror(wa[2])
        txt.print_ubbin(wa[2], true)
        txt.nl()
        ror2(wa[2])
        txt.print_ubbin(wa[2], true)
        txt.nl()
        txt.nl()
    }

    sub words() {
        uword[]  wa = [$11aa, $22bb, $33cc]
        uword[]  @split swa = [$11aa, $22bb, $33cc]
        uword[]  waone = [$0000, $3300, $0000]
        uword[]  wazero = [$0000, $0000, $0000]
        uword[]  @split swaone = [$0000, $3300, $0000]
        uword[]  @split swazero = [$0000, $0000, $0000]

;        txt.print_ub(all(wa))        ; 1
;        txt.spc()
;        txt.print_ub(any(wa))        ; 1
;        txt.nl()
;        txt.print_ub(all(waone))     ; 0
;        txt.spc()
;        txt.print_ub(any(waone))     ; 1
;        txt.nl()
;;        txt.print_ub(all(swaone))    ; 0
;;        txt.spc()
        txt.print_ub(any(swaone))    ; 1
        txt.nl()
;;        txt.print_ub(all(swa))       ; 1
;;        txt.spc()
;        txt.print_ub(any(swa))       ; 1
;        txt.nl()
;        txt.print_ub(all(wazero))    ; 0
;        txt.spc()
;        txt.print_ub(any(wazero))    ; 0
;        txt.nl()
;;        txt.print_ub(all(swazero))   ; 0
;;        txt.spc()
        txt.print_ub(any(swazero))   ; 0
        txt.nl()

;        txt.print_uwbin(wa[2], true)
;        txt.nl()
;        rol(wa[2])
;        txt.print_uwbin(wa[2], true)
;        txt.nl()
;        rol2(wa[2])
;        txt.print_uwbin(wa[2], true)
;        txt.nl()
;        ror(wa[2])
;        txt.print_uwbin(wa[2], true)
;        txt.nl()
;        ror2(wa[2])
;        txt.print_uwbin(wa[2], true)
;        txt.nl()
;        txt.nl()
;
;        txt.print_uwbin(swa[2], true)
;        txt.nl()
;        rol(swa[2])
;        txt.print_uwbin(swa[2], true)
;        txt.nl()
;        rol2(swa[2])
;        txt.print_uwbin(swa[2], true)
;        txt.nl()
;        ror(swa[2])
;        txt.print_uwbin(swa[2], true)
;        txt.nl()
;        ror2(swa[2])
;        txt.print_uwbin(swa[2], true)
;        txt.nl()
;        txt.nl()
    }
}
