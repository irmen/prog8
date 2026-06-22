%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub bank_selector(ubyte call_id) -> ubyte {
        txt.print(">>bselect. callid=")
        txt.print_ub(call_id)
        txt.print("<<\n")
        return 4
    }

    extsub @bank bank_selector $ffd2 = chrout(ubyte char @A)
    extsub @bank bank_selector $ffd2 = anotherchrout(ubyte char @A)
    extsub @bank bank_selector $ffd2 = yetanotherchrout(ubyte char @A)

    sub start() {
        chrout('a')
        txt.nl()
        anotherchrout('b')
        txt.nl()
        yetanotherchrout('c')
        txt.nl()
    }
}
