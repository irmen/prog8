%import c64utils
%import c64flt
%option enable_floats
%zeropage basicsafe

main {

    struct Color {
        uword red
        uword green
        uword blue
    }

    sub start() {
        Color c = {1,2,3}
        Color c2 = {3,4,5}
        c=c2
        c= {1,2,3}          ; TODO fix compiler crash AssemblyError: struct literal value assignment should have been flattened
    }

 }
