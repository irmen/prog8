%import textio
%zeropage basicsafe


main {
    sub ftrue(ubyte arg) -> ubyte {
        arg++
        txt.print(" ftrue ")
        return 1
    }

    sub start() {
        bool ub1 = true
        bool ub2 = true
        bool ub3 = true
        bool ub4 = 0
        bool bvalue

        txt.print("expected output: 0 ftrue 0 ftrue 1\n")
        bvalue = ub1 xor ub2 xor ub3 xor true
        txt.print_ub(bvalue)
        txt.spc()
        bvalue = ub1 xor ub2 xor ub3 xor ftrue(99)
        txt.print_ub(bvalue)
        txt.spc()
        bvalue = ub1 and ub2 and ftrue(99)
        txt.print_ub(bvalue)
        txt.nl()
    }
}
