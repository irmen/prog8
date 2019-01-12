%import c64utils

~ main {

    sub start()  {

        str question = "how are you?\n"

        ; use iteration to write text
        for ubyte char in question {            ; @todo fix iteration
            ;vm_write_char(char)
            c64.CHROUT(char)
        }

    }
}
