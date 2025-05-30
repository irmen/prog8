%import textio
%zeropage basicsafe

main {
    sub start() {

        other.foo.listarray[2] = 2000
        pokew(2001, 12345)
        txt.print_uw(other.foo.listarray[2].value)
        txt.nl()
        pokew(2001, 22222)
        other.foo()

        ; TODO fix parsing of this assignment target:
        ;other.foo.listarray[2].value = 33333
        ;other.foo.listarray[2]^^.value = 44444
        ;txt.print_uw(other.foo.listarray[2].value)
        ;txt.nl()
    }
}

other {
    sub foo() {
        struct List {
            bool b
            uword value
        }

        ^^List[10] listarray
        ^^bool[10] barray

        txt.print_uw(listarray[2].value)
        txt.nl()

        ; TODO fix parsing of this assignment target:
        ; listarray[2].value = 9999
    }
}
