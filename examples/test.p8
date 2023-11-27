%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        ubyte[100] storage = 0

        txt.print("sizeof float = ")
        txt.print_ub(sizeof(0.0))
        txt.print("\nsizeof word = ")
        txt.print_ub(sizeof($0000))
        txt.print("\nsizeof byte = ")
        txt.print_ub(sizeof($00))
        txt.print("\nsizeof bool = ")
        txt.print_ub(sizeof(true))
        txt.nl()

        poke(&storage+10, 123)
        pokew(&storage+11, 54321)
        txt.print_ub(peek(&storage+10))
        txt.spc()
        txt.print_uw(peekw(&storage+11))
        txt.nl()

        pokef(&storage+10, 3.14)
        pokef($4000, 123.456)
        floats.print_f(peekf(&storage+10))
        txt.nl()
        floats.print_f(peekf($4000))
        txt.nl()
        pokef(&storage+10, 3.1415927)
        floats.print_f(peekf(&storage+10))
        txt.nl()

        for cx16.r2L in 0 to 20 {
            txt.print_ubhex(storage[cx16.r2L], false)
            txt.spc()
        }
        txt.nl()
    }
}
