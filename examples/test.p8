%import textio
%import syslib
%import floats
%zeropage basicsafe


main {


;    Color c1 = [11,22,33]       ; TODO fix crash
;    Color c2 = [11,22,33]       ; TODO fix crash
;    Color c3 = [11,22,33]       ; TODO fix crash
;    uword[] colors = [ c1, c2, c3]      ; TODO should contain pointers to (the first element) of each struct


    str[] names = ["aap", "noot", "mies", "vuur"]
    uword[] names3 = ["aap", "noot", "mies", "vuur"]
    ubyte[] values = [11,22,33,44]
    uword[] arrays = [names, names3, values]


    sub start() {
        byte bb = 100
        word ww = 30000
        float ff1 = 12345
        float ff2 = -99999

        ;ff = 1+((-ff) *3)       ; TODO fix invalid splitting (can't split because it references ff itself)
        ;ff = 1+((-ff2) *3)       ; TODO splitting should be okay here

        floats.print_f(ff1)     ; TODO if we remove this, the following calcuation is wrong
        txt.chrout('\n')
        ff1 = -ff2 * 3
        floats.print_f(ff1)
        txt.chrout('\n')
        return

        struct Color {
            ubyte red
            ubyte green
            ubyte blue
        }

        ;Color c1 = [11,22,33]           ; TODO fix struct initializer crash
        Color c1
        Color c2
        Color c3
        ;Color c2 = [11,22,33]
        ;Color c3 = [11,22,33]
        ;uword[] colors = [ c1, c2, c3]      ; TODO should contain pointers to (the first element) of each struct

        c1 = c2
        ;c1 = [11,22,33]         ; TODO rewrite into individual struct member assignments


        uword s
        for s in names {
            txt.print(s)
            txt.chrout('\n')
        }
        txt.chrout('\n')

        txt.print(names[2])
        txt.chrout('\n')
        txt.print(names[3])
        txt.chrout('\n')

        repeat {
            txt.print(names3[rnd()&3])
            txt.chrout(' ')
        }
    }

}
