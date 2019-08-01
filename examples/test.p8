%zeropage basicsafe

main {

    sub start() {
        c64.CHROUT('\n')
        %asm {{
            stx $0410
        }}
        c64.CHRIN()
        %asm {{
            stx $0411
        }}
        print_notes(80,35)
        %asm {{
            stx $0412
        }}
        return
    }

    sub print_notes(ubyte n1, ubyte n2) {
        c64scr.print_ub(n1/2)
        c64.CHROUT('\n')
        c64scr.print_ub(n1/3)
        c64.CHROUT('\n')
        c64scr.print_ub(n1/4)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        c64scr.print_ub(n2/2)
        c64.CHROUT('\n')
        c64scr.print_ub(n2/3)
        c64.CHROUT('\n')
        c64scr.print_ub(n2/4)
        c64.CHROUT('\n')
    }
}
