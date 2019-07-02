%import c64utils
%zeropage basicsafe
%import c64flt

~ main {

    sub start() {

        c64.TIME_HI = 22
        c64.TIME_MID = 33
        c64.TIME_LO = 44

        loop:
            ubyte hi = c64.TIME_HI
            ubyte mid = c64.TIME_MID
            ubyte lo = c64.TIME_LO

            c64scr.plot(0,0)
            c64scr.print_ub0(hi)
            c64scr.print("   \n")
            c64scr.print_ub0(mid)
            c64scr.print("   \n")
            c64scr.print_ub0(lo)
            c64scr.print("   \n")

            uword x = mkword(c64.TIME_LO, c64.TIME_MID)
            c64scr.print_uw(x)
            c64scr.print("     \n")

            float clock_seconds_f = ((mkword(c64.TIME_LO, c64.TIME_MID) as float) + (c64.TIME_HI as float)*65536.0) / 60.0
            c64flt.print_f(clock_seconds_f)
            c64scr.print("     \n")
            float hours_f = floor(clock_seconds_f / 3600.0)
            clock_seconds_f -= hours_f*3600.0
            float minutes_f = floor(clock_seconds_f / 60.0)
            clock_seconds_f = floor(clock_seconds_f - minutes_f * 60.0)

            c64flt.print_f(hours_f)
            c64.CHROUT(':')
            c64flt.print_f(minutes_f)
            c64.CHROUT(':')
            c64flt.print_f(clock_seconds_f)
            c64scr.print("    \n")

            ubyte hours = hours_f as ubyte
            ubyte minutes = minutes_f as ubyte
            ubyte seconds = clock_seconds_f as ubyte
            c64scr.print_ub(hours)
            c64.CHROUT(':')
            c64scr.print_ub(minutes)
            c64.CHROUT(':')
            c64scr.print_ub(seconds)
            c64scr.print("    \n")

            goto loop

    }


;        for ubyte y in 0 to 3 {
;            for ubyte x in 0 to 10 {
;                ubyte product = x*y
;                c64scr.setcc(x, y, 160, product)
;            }
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        for ubyte y in 12 to 15 {
;            for ubyte x in 0 to 10 {
;                ubyte sumv = x+y
;                c64scr.setcc(x, y, 160, sumv)
;            }
;        }

        ;ubyte bb = len(xcoor)

        ; storage for rotated coordinates
;        ubyte[len(xcoor)] xx = 2
;        float[len(xcoor)] rotatedx=0.0
;
;        ubyte[4] x = 23
;        float[4] yy = 4.4

;        c64flt.print_f(xcoor[1])
;        c64.CHROUT(',')
;        c64flt.print_f(xcoor[2])
;        c64.CHROUT('\n')
;        swap(xcoor[1], xcoor[2])
;        c64flt.print_f(xcoor[1])
;        c64.CHROUT(',')
;        c64flt.print_f(xcoor[2])
;        c64.CHROUT('\n')

}
