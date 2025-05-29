%zeropage basicsafe

main {
    sub start() {
        ^^bool[10] barray

        struct List {
            bool b
            word w
        }

        ^^List[10] listarray

        cx16.r0 = listarray[2]^^.value
        ;listarray[2]^^.value = 4242

;        listarray[2]^^.value = 4242
;        bool z = barray[2]^^
        ;barray[2]^^ = true
    }
}
