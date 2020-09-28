%import textio
%import syslib
%zeropage basicsafe


main {

    struct Color {
        ubyte red
        ubyte green
        ubyte blue
    }

;    Color c1 = [11,22,33]       ; TODO fix crash
;    Color c2 = [11,22,33]       ; TODO fix crash
;    Color c3 = [11,22,33]       ; TODO fix crash
;    uword[] colors = [ c1, c2, c3]      ; TODO should contain pointers to (the first element) of each struct


    str[] names = ["aap", "noot", "mies", "vuur"]
    uword[] names3 = ["aap", "noot", "mies", "vuur"]
    ubyte[] values = [11,22,33,44]
    uword[] arrays = [names, names3, values]


    sub start() {
        Color c1 = [11,22,33]
        Color c2 = [11,22,33]
        Color c3 = [11,22,33]
        uword[] colors = [ c1, c2, c3]      ; TODO should contain pointers to (the first element) of each struct

        c1.red = 100
        c1.green = 100
        c1.blue = 100
        ; c1 = [11,22,33]         ; TODO rewrite into individual struct member assignments


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
