%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        uword xx = &b2.zz
        xx=&b3.zz

        txt.print_uwhex(&main, true)
        txt.nl()
        txt.print_uwhex(&b2, true)
        txt.nl()
        txt.print_uwhex(&b3, true)
        txt.nl()
    }
}

b2 {
    str zz="hello"
}

b3 $4001 {
    str zz="bye"
}
