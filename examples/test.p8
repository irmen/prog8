%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ubyte A=5
        uword clr = $d020
        A = @(clr)
        A++
        @(clr) = A

;        uword xx = @(clr+1)
    }
}
