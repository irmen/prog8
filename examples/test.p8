%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        str[] names = ["a", "aa", "harry", "the Quick Brown Fox jumps Over the LAZY dog!"]

        uword name
        for name in names {
            txt.print_ub(string.hash(name))
            txt.spc()
            txt.print(name)
            txt.nl()
        }
    }
}
