%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {
;        float fl = 123.4567
;        c64flt.print_f(round(fl))
;        c64.CHROUT('\n')
;        c64flt.print_f(round(fl))
;        c64.CHROUT('\n')
;        c64flt.print_f(round(fl))
;        c64.CHROUT('\n')
;        c64flt.print_f(ceil(fl))
;        c64.CHROUT('\n')
;        c64flt.print_f(ceil(fl))
;        c64.CHROUT('\n')
;        c64flt.print_f(ceil(fl))
;        c64.CHROUT('\n')
;        c64flt.print_f(floor(fl))
;        c64.CHROUT('\n')
;        c64flt.print_f(floor(fl))
;        c64.CHROUT('\n')
;        c64flt.print_f(floor(fl))
;        c64.CHROUT('\n')
;        @($040a)=X
;        return

        while true {
        float clock_seconds = ((mkword(c64.TIME_LO, c64.TIME_MID) as float) + (c64.TIME_HI as float)*65536.0) / 60
        float hours = floor(clock_seconds / 3600)
        clock_seconds -= hours*3600
        float minutes = floor(clock_seconds / 60)
        clock_seconds = floor(clock_seconds - minutes * 60.0)

        c64scr.print("system time in ti$ is ")
        c64flt.print_f(hours)
        c64.CHROUT(':')
        c64flt.print_f(minutes)
        c64.CHROUT(':')
        c64flt.print_f(clock_seconds)
        c64.CHROUT('\n')
        }
    }
}
