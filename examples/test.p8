%import c64lib
%import c64utils
%import c64flt


~ main {

    sub start() {

        ubyte ub1
        uword uw1

        float clock_seconds = ((c64.TIME_LO as float) + 256.0*(c64.TIME_MID as float) + 65536.0*(c64.TIME_HI as float)) / 60
        float hours = floor(clock_seconds / 3600)
        clock_seconds -= hours * 3600
        float minutes = floor(clock_seconds / 60)
        clock_seconds -= minutes * 60.0

        ubyte hours_b = hours as ubyte
        ubyte minutes_b = minutes as ubyte
        ubyte seconds_b = clock_seconds as ubyte

    }

}
