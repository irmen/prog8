%zeropage basicsafe

main {
    sub start() {

        cx16.r0 = other.foo.listarray[2].value
        cx16.r1 = other.foo.listarray[2]^^.value

        other.foo()

        ;listarray[2]^^.value = 4242

;        listarray[2]^^.value = 4242
;        bool z = barray[2]^^
        ;barray[2]^^ = true
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

        cx16.r0 = listarray[2].value
        cx16.r1 = listarray[2]^^.value
    }
}
