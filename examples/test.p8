%import textio
%import timer
%import arexx
%import exec

main {
    sub start() {

        if timer.opendevice() {
            ;; TODO fix   long hi,lo = timer.getsystime()
            long hi,lo
            hi,lo = timer.getsystime()
            txt.print_l(hi)
            txt.spc()
            txt.print_l(lo)
            txt.nl()
            timer.setsystime(123,456)
            hi,lo = timer.getsystime()
            txt.print_l(hi)
            txt.spc()
            txt.print_l(lo)
            txt.nl()
            timer.closedevice()
        }


        if arexx.openlib() {
            txt.print_ulhex(sys.RexxSysBase, true)
            txt.nl()

            pointer a = arexx.CreateArgstring("irmen", 5)
            if a!=0 {
                txt.print_ulhex(a, true)
                txt.nl()
                arexx.DeleteArgstring(a)
            }

            arexx.closelib()
        } else {
            txt.print("no lib\n")
        }

    }
}
