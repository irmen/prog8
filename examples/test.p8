%import floats

main {
    sub start() {

        singleparamb(10)
        singleparamw(2000)
        singleparamf(1.23456)
    }

    sub singleparamb(ubyte bb) {
        bb++
    }

    sub singleparamw(word ww) {
        ww++
    }

    sub singleparamf(float ff) {
        ff++
    }
}
