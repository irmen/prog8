%import c64utils
%zeropage basicsafe

main {

    sub start() {

        subje(12345)
    }

    sub subje(uword xx) {
        @($c000) = lsb(xx)
    }
}

