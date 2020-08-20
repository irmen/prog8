%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ubyte A=5
        uword clr = $d020
        @(clr+1) = A

;        uword xx = @(clr+1)
    }
}
