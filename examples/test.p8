%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte bank = cx16.search_x16edit()
        txt.print_ub(bank)
        if bank<255 {
            cx16.rombank(bank)
            cx16.x16edit_default()
            cx16.rombank(0)
        }
        txt.print("back from editor!\n")
    }
}
