%import textio
%zeropage basicsafe

main {
    sub start() {
        basic_area.routine1()
    }
}

basic_area $a000 {
    sub routine1() {
        txt.print("hello from basic rom area ")
        txt.print_uwhex(&routine1, true)
        txt.nl()
    }
}

hiram_area $c000 {
    %option force_output
    sub thing() {
        cx16.r0++
    }
}
