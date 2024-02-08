%import textio
%import string

%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        foo("zzz")
    }

    sub foo (str s2) {
        str s = "irmen"
        txt.print_uwhex(s, true)
        txt.nl()
        txt.print_uwhex(&s, true)
        txt.nl()
        txt.print_uwhex(&s[2], true)
        txt.nl()
        txt.nl()
        txt.print_uwhex(s2, true)
        txt.nl()
        txt.print_uwhex(&s2, true)
        txt.nl()
        txt.print_uwhex(s2+2, true)
        txt.nl()
        txt.print_uwhex(&s2[2], true)   ; TODO should be the same as the previous one!
        txt.nl()
    }
}
