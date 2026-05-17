%import textio
%zeropage basicsafe

main {
    long one
    long two
    long three

    sub start() {
        one = 1432
        two = 23        ; 1432/60 integer
        three = 1380    ; 23*60
        txt.print_l((1432 - three) * 1)
        txt.nl()
        txt.print_l((1432 - (two * 60)) * 1)
        txt.nl()

        ;sys.poweroff_system()
    }
}
