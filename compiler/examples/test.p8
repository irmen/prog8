%import c64utils

~ main {

    ubyte[3] ubarray = [11,55,222]
    byte[3] barray = [-11,-22,-33]
    uword[3] uwarray = [111,2222,55555]
    word[3] warray = [-111,-222,-555]
    str text = "hello\n"

    sub start()  {
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        c64scr.print("loop str\n")
        for ubyte c in text {
            c64scr.print(" c ")
            c64scr.print_ub(c)
            c64.CHROUT('\n')
        }

        c64scr.print("loop ub\n")
        for ubyte ub in ubarray{
            c64scr.print(" ub ")
            c64scr.print_ub(ub)
            c64.CHROUT('\n')
        }

;        c64scr.print("loop b\n")    ; @todo allow signed loopvars
;        for byte b in barray {      ; @todo loop doesn't end because of register clobbering??
;            c64scr.print(" b ")
;            c64scr.print_b(b)
;            c64.CHROUT('\n')
;        }

errorloop:
        c64scr.print("loop uw\n")
        for uword uw in uwarray {       ; @todo loop doesn't end because of register clobbering
            c64scr.print(" uw ")
            c64scr.print_uw(uw)
            c64.CHROUT('\n')
        }

ending:
        c64scr.print("\nending\n")
        c64scr.print_ub(X)
        c64.CHROUT('\n')
    }
}
