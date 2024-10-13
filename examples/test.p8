main {
    sub start() {
label:
        str @shared name = "name"
        ubyte @shared bytevar
        uword[] @shared array = [name, label, start, main, 9999]
        uword[] @shared array2 = [&name, &label, &start, &main, 9999]
        uword[] @shared array3 = [cx16.r0]  ; error, is variables
        uword[] @shared array4 = [bytevar]  ; error, is variables
    }
}
