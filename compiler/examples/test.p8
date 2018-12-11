%output raw
%launcher none

~ main {

    sub start()  {
        ending(true)
        return  ; @todo make return ending(true) actually work as well

        return 99               ;@todo error message (no return values)
        return 99,44         ;@todo error message (no return values)
        return ending(false)        ; @todo fix this, actuall needs to CALL ending even though no value is returned

        sub ending(success: ubyte) {
            return 99       ; @todo error message (no return values)
            return 99,44       ; @todo error message (no return values)
            return 99,44    ; @todo should check number of return values!!
        }

        sub ending2() -> ubyte {
            return
            return 1
            return 2, 2     ; @todo error message number of return values
        }
    }
}
