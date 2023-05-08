%import textio
%zeropage basicsafe

main {

    word[5] dx = [111,222,333,444,555]

    sub start() {
        uword hit_x = 999
        cx16.r0=2
        uword new_head_x = hit_x + dx[cx16.r0L] as uword
        txt.print_uw(new_head_x)
    }
}

