%import textio
%import math
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        uword[] array = [$1010,$2020,$3030,$4040,$5050]
        ubyte index = 2
        uword value = $0205

        array[index] ^= $0205
        txt.print_uwhex(array[2], true)
        txt.nl()

        array[index]+=9

        txt.print_uwhex(array[2], true)
        txt.nl()

        array[index] = $3030
        array[index] |= value
        txt.print_uwhex(array[2], true)
        txt.nl()

        ; TODO met var array[index]|=index
    }
}
