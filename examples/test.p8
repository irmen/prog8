%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared bb

        when bb {
            0,1,2 -> {
                bb ++
            }
            3 -> {
                bb--
            }
            else -> {
                bb=0
            }
        }
    }
}
