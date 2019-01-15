%import c64lib
%import c64utils

~ main {

    sub start() {

        ; set screen colors and activate lowercase charset
        c64.EXTCOL = 5
        c64.BGCOL0 = 0
        c64.COLOR = 1
        c64.VMCSB |= 2

        ; use optimized routine to write text
        c64scr.print("Hello!\n")

        ; use iteration to write text
        str question = "How are you?\n"
        for ubyte char in question {            ; @todo allow for/while/repeat loops without curly braces just like ifs
            c64.CHROUT(char)
        }

        ; use indexed loop to write characters
        str bye = "Goodbye!\n"
        for ubyte c in 0 to len(bye) {
            c64.CHROUT(bye[c])
        }

    }

}
