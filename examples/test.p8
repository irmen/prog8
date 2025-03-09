%import textio
%import strings
%zeropage basicsafe

main {

    str name1 = "irmen"
    str name2 = "irmen de jong"

    sub start()  {

        txt.print_b(strings.compare(name1, name2))
        txt.spc()
        txt.print_b(strings.compare(name2, name1))
        txt.nl()

        txt.print_b(strings.ncompare(name1, name2, 6))
        txt.spc()
        txt.print_b(strings.ncompare(name2, name1, 6))
        txt.nl()

        txt.print_b(strings.ncompare(name1, name2, 5))
        txt.spc()
        txt.print_b(strings.ncompare(name2, name1, 5))
        txt.nl()

        txt.print_b(strings.ncompare(name1, name2, 4))
        txt.spc()
        txt.print_b(strings.ncompare(name2, name1, 4))
        txt.nl()
    }
}
