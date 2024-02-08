%import textio
%import string

%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name1 = "name1"
        str name2 = "name2"
        uword[] @split names = [name1, name2, "name3"]
        uword[] addresses = [0,0,0]
        names = [1111,2222,3333]
        addresses = names
        ;foo("zzz")
    }

    sub foo (str sarg) {
        ubyte[3] svar
        txt.print_uwhex(svar, true)
        txt.nl()
        txt.print_uwhex(&svar, true)
        txt.nl()
        txt.print_uwhex(&svar[2], true)
        txt.nl()
        cx16.r1L = 3
        txt.print_uwhex(&svar[cx16.r1L], true)
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
        cx16.r1L=3
        cx16.r0 = &sarg[cx16.r1L]
        txt.print_uwhex(cx16.r0, true)   ; TODO should be the same as the previous one  sarg+2 (13)!
        txt.nl()
    }
}
