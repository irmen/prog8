%import c64utils
%zeropage basicsafe

~ main {

    sub start() {

        foo(42)
        return
    }

    sub foo(ubyte arg) -> ubyte {
        bar(arg)
        return 33
    }

    sub bar(ubyte a2) {
        ;nothing
    }

}
