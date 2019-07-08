%import c64utils
%zeropage basicsafe

~ main {

    sub start() {

        uword xw = 33

        ; TODO reorderstatements fucks up the order of these
        xw = $d020              ; @todo
        @(xw) = 1                   ; @todo should turn border white
        @(xw) = 1                   ; @todo should turn border white

        Y=2            ; @todo gets removed in assembly???!!!
        A=Y
        @($d021)=Y
        @(xw) = Y
    }


    asmsub derp (ubyte arg @ X) -> clobbers(A, X) -> (ubyte @Y) = $a000

}
