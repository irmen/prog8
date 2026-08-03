%import textio
%import exec
%import timer

main {
    sub start() {

        long secs, micro = timer.getsystime()
        txt.print_l(secs)
        txt.spc()
        txt.print_l(micro)
        txt.nl()

        timer.setsystime(9,99999)
        secs, micro = timer.getsystime()
        txt.print_l(secs)
        txt.spc()
        txt.print_l(micro)
        txt.nl()

;        ^^timer.EClockVal eclock = []
;        timer.GetSysTime(systime)
;        txt.print_l(systime.secs)
;        txt.spc()
;        txt.print_l(systime.micro)
;        txt.nl()
;
;        long tickrate = timer.ReadEClock(eclock)
;        txt.print_l(tickrate)
;        txt.nl()
;        txt.print_ulhex(eclock.hi,true)
;        txt.spc()
;        txt.print_ulhex(eclock.lo,false)
;        txt.nl()
    }
}
