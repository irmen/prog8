%import textio
%zeropage basicsafe
%option no_sysinit

main {

;   $1F9C0 - $1F9FF 	PSG registers

    sub start() {
        uword xx = &b2.zz
        xx=&b3.zz
        xx=&b4.zz
        xx=&b5.zz

        txt.print_uwhex(&main, true)
        txt.nl()
        txt.print_uwhex(&b2, true)
        txt.nl()
        txt.print_uwhex(&b3, true)
        txt.nl()
        txt.print_uwhex(&b4, true)
        txt.nl()
        txt.print_uwhex(&b5, true)
        txt.nl()
    }
}

b2 {
    str zz="hello"
}

b3 $4001 {
    str zz="bye"
}

b4 {
    %option align_word

    str zz="wut"
}

b5 {
    %option align_page

    str zz="wut2"
}
