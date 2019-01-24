%import c64lib
%import c64utils
%import c64flt


~ main {

    sub start() {

        float clock_seconds = ((mkword(c64.TIME_LO, c64.TIME_MID) as float) + (c64.TIME_HI as float)*65536.0) / 60
        float hours = floor(clock_seconds / 3600)
        clock_seconds -= hours*3600
        float minutes = floor(clock_seconds / 60)
        clock_seconds -= minutes * 60.0

        ubyte hours_b = hours as ubyte
        ubyte minutes_b = minutes as ubyte
        ubyte seconds_b = clock_seconds as ubyte

        c64scr.print_ub(hours_b)
        c64.CHROUT(':')
        c64scr.print_ub(minutes_b)
        c64.CHROUT(':')
        c64scr.print_ub(seconds_b)
        c64.CHROUT('\n')


    }

}
