%import textio
%zeropage basicsafe

main {
    sub start() {
        compares(10)
        compares(-10)
        compares(0)
        compares($00aa0000)
        compares($ffaa0000)

        sub compares(long value) {
            txt.print_l(value)
            txt.spc()
            txt.spc()
            if value>0
                txt.print(" >0 ")
            if value >=0
                txt.print(" >=0 ")
            if value < 0
                txt.print(" <0 ")
            if value<=0
                txt.print(" <=0")

            txt.nl()
        }
    }
}
