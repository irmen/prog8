%import textio
%import string
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        str name1 = "alfred"
        str name2 = "aldrik"
        str name3 = "aldrik"

        uword block1 = memory("block1", 1000, 0)
        uword block2 = memory("block2", 1000, 0)
        uword block3 = memory("block3", 1000, 0)

        sys.memset(block1, 1000, 0)
        sys.memset(block2, 1000, 0)
        sys.memset(block3, 1000, 0)
        void string.copy(name1, block1+900)
        void string.copy(name2, block2+900)
        void string.copy(name3, block3+900)

        txt.print_b(string.compare(name1, name2))
        txt.spc()
        txt.print_b(string.compare(name2, name3))
        txt.spc()
        txt.print_b(string.compare(name2, name1))
        txt.nl()
        txt.print_b(sys.memcmp(name1, name2, len(name1)))
        txt.spc()
        txt.print_b(sys.memcmp(name2, name3, len(name1)))
        txt.spc()
        txt.print_b(sys.memcmp(name2, name1, len(name1)))
        txt.nl()
        txt.nl()

        name1[1] = 0
        name2[1] = 0
        name3[1] = 0
        txt.print_b(string.compare(name1, name2))
        txt.spc()
        txt.print_b(string.compare(name2, name3))
        txt.spc()
        txt.print_b(string.compare(name2, name1))
        txt.nl()

        txt.print_b(sys.memcmp(name1, name2, len(name1)))
        txt.spc()
        txt.print_b(sys.memcmp(name2, name3, len(name1)))
        txt.spc()
        txt.print_b(sys.memcmp(name2, name1, len(name1)))
        txt.nl()

        txt.print_b(sys.memcmp(block1, block2, 1000))
        txt.spc()
        txt.print_b(sys.memcmp(block2, block3, 1000))
        txt.spc()
        txt.print_b(sys.memcmp(block2, block1, 1000))
        txt.nl()
    }
}
