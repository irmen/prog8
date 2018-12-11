%import c64utils

~ main {

    sub start()  {
        return ending(true)     ;; @todo fix argument passing!

        sub ending(success: ubyte) {
            c64scr.print_byte_decimal(success)
            c64scr.print_byte_decimal(success)
            c64scr.print_byte_decimal(success)
            c64.CHROUT('\n')
        }
    }
}
