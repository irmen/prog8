%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        str s1 = "irmen"
        ubyte ff = 1
        txt.print(&s1+ff)
        txt.nl()
        txt.print(&s1+ff)
        txt.nl()
        txt.print_uwhex(&s1+ff, true)


        ubyte[] array = [1,2,3,4]
        txt.print_uwhex(&array+ff, true)
        txt.nl()
        txt.print_uwhex(&array+ff, true)
        txt.nl()
    }
}
