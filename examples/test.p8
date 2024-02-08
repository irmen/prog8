%import textio
%import string

%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        foo("zzz")
    }

    sub foo (str sarg) {
        str svar = "irmen"
        txt.print_uwhex(svar, true)
        txt.nl()
        txt.print_uwhex(&svar, true)
        txt.nl()
        txt.print_uwhex(&svar[2], true)
        txt.nl()
        txt.nl()
        txt.print_uwhex(sarg, true)
        txt.nl()
        txt.print_uwhex(&sarg, true)
        txt.nl()
        txt.print_uwhex(sarg+2, true)
        txt.nl()
        cx16.r0 = &sarg[2]
        txt.print_uwhex(cx16.r0, true)   ; TODO should be the same as the previous one  sarg+2 (13)!
        txt.nl()
    }
}
