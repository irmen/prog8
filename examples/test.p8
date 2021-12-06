%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared bb

        if bb + sin8u(bb) > 100-bb {
            bb++
        }

        while bb + sin8u(bb) > 100-bb {
            bb++
        }

        do {
            bb++
        } until  bb + sin8u(bb) > 100-bb

        const ubyte EN_TYPE=2
        uword eRef = $c000
        ubyte chance = rnd() % 100

        if eRef[EN_TYPE] and chance < (eRef[EN_TYPE] << 1) {
            bb++
        }

    }
}
