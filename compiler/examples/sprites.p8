%import c64lib
%import c64utils

~ main {

    sub start() {

        c64.STROUT("balloon sprites!\n")

        const uword SP0X = $d000    ; @todo "address-of" operator '&' so we can write  &c64.SP0X
        const uword SP0Y = $d001    ; @todo "address-of" operator '&' so we can write  &c64.SP0Y

        for ubyte i in 0 to 7 {
            @(SP0X+i*2) = 30+i*30
            @(SP0Y+i*2) = 100+i*10
        }

        c64.SPENA = 255     ; enable all sprites
    }

}
