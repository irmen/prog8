%import c64utils
%zeropage basicsafe

~ main {

    sub start() {

        uword xw = 33

        xw = $d020              ; @todo gets removed in assembly!?!??!?!?
        @(xw) = 1                   ; @todo should turn border white
        @(xw) = 1                   ; @todo should turn border white

;        Y=12            ; @todo gets removed in assembly???!!!
;        A=Y
;        A=99
;        @($d021)=A
;        @(xw) = A
    }

}
