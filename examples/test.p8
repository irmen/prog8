%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe



main {

    sub jumpsub() {

        ; goto jumpsub        ; TODO fix compiler loop
        goto blabla
blabla:
        A=99
        return

    }

    sub start() {

        str sss = "zzz"
        str x = "zxcvzxcv"

        x = @(&sss)
    }

}


