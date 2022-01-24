%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        str s1 = "irmen@razorvine.net"

        ubyte ff = string.find(s1, '@')
        if_cs {
            txt.print_uwhex(&s1+ff, true)
            txt.spc()
            txt.print(&s1+ff)
            txt.nl()
        }

        ff = string.find(s1, 'i')
        if_cs {
            txt.print_uwhex(&s1+ff, true)
            txt.spc()
            txt.print(&s1+ff)
            txt.nl()
        }

        ff = string.find(s1, 't')
        if_cs {
            txt.print_uwhex(&s1+ff, true)
            txt.spc()
            txt.print(&s1+ff)
            txt.nl()
        }

        ff = string.find(s1, 'q')
        if_cs {
            txt.print_uwhex(&s1+ff, true)
            txt.spc()
            txt.print(&s1+ff)
            txt.nl()
        }

        ; txt.print_uwhex(s1+ff, true)        ; TODO fix compiler crash on s1+ff.   why no crash when using 1-argument functioncall?
    }
}
