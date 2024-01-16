%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        word @shared q = -12345
        txt.print_w(q)
        txt.nl()
        txt.print_uwbin(q as uword, true)
        txt.nl()
        q >>=9
        txt.print_w(q)
        txt.nl()
        txt.print_uwbin(q as uword, true)
        txt.nl()

;        mem()
;        bytes()
;        words()
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

        txt.print_uwbin(wa[2], true)
        txt.nl()
        rol(wa[2])
        txt.print_uwbin(wa[2], true)
        txt.nl()
        rol2(wa[2])
        txt.print_uwbin(wa[2], true)
        txt.nl()
        ror(wa[2])
        txt.print_uwbin(wa[2], true)
        txt.nl()
        ror2(wa[2])
        txt.print_uwbin(wa[2], true)
        txt.nl()
        txt.nl()

        txt.print_uwbin(swa[2], true)
        txt.nl()
        rol(swa[2])
        txt.print_uwbin(swa[2], true)
        txt.nl()
        rol2(swa[2])
        txt.print_uwbin(swa[2], true)
        txt.nl()
        ror(swa[2])
        txt.print_uwbin(swa[2], true)
        txt.nl()
        ror2(swa[2])
        txt.print_uwbin(swa[2], true)
        txt.nl()
        txt.nl()
    }
}
