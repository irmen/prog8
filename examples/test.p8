%import c64utils
%zeropage basicsafe

~ main {

    Color blocklevelcolor

    sub start() {

        Color subcol

        A=msb(subcol.red)
        for ubyte i in 10 to 20 {
            ;A=subcol.red
            ;A=blocklevelcolor.green

            ;subcol.blue = Y
            ;blocklevelcolor.green=Y
            A=msb(subcol.red)
        }
        return
    }

    struct Color {
        ubyte red
        ubyte green
        ubyte blue
    }

}
