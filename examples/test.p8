%import textio

main {

    sub start() {

        repeat 100 {
            random_rgb12()
            txt.print_ubhex(target_red,false)
            txt.print_ubhex(target_green,false)
            txt.print_ubhex(target_blue,false)
            txt.nl()
        }

        repeat {
        }
    }

        ubyte target_red
        ubyte target_green
        ubyte target_blue

        sub random_rgb12() {
            do {
                uword rr = rndw()
                target_red = msb(rr) & 15
                target_green = lsb(rr)
                target_blue = target_green & 15
                target_green >>= 4
            } until target_red+target_green+target_blue >= 12
        }

}
