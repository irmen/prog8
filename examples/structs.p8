%import c64utils
%zeropage basicsafe

main {

    struct Color {
        ubyte red
        ubyte green
        ubyte blue
    }

    sub start() {

        Color purple = {255, 0, 255}

        Color other

        other = purple

        other.red /= 2
        other.green = 10 + other.green / 2
        other.blue = 99

        c64scr.print_ub(other.red)
        c64.CHROUT(',')
        c64scr.print_ub(other.green)
        c64.CHROUT(',')
        c64scr.print_ub(other.blue)
        c64.CHROUT('\n')
    }
}
