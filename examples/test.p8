%import textio
%zeropage basicsafe

main {
    sub start() {
        alignblock.flags[0] = 222
        cx16.r0++
        cx16.r1++
        txt.print_uwhex(alignblock.flags, true)
        txt.spc()
        txt.print_ub(alignblock.flags[0])
        txt.nl()
    }
}

alignblock {
    %option align_page
    ubyte[10] flags
}

