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
        ; Color c = [1,2,3]       ; TODO fix compiler error
        Color c = {1,2,3}
    }

 }
