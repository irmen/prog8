%zeropage basicsafe

~ main {
        &uword COLORS = $d020

    sub start() {

        COLORS=12345
        COLORS=12346
        @(COLORS) = 54

        return

    }
}
