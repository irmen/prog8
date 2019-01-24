%import c64lib
%import c64utils
%import c64flt


~ main {

    sub start() {

        ; set text color and activate lowercase charset
        c64.COLOR = 13
        c64.VMCSB |= 2

        ; use optimized routine to write text
        c64scr.print("Hello!\n")

        ; use iteration to write text
        str question = "How are you?\n"
        for ubyte char in question
            c64.CHROUT(char)

        ; use indexed loop to write characters
        str bye = "Goodbye!\n"
        for ubyte c in 0 to len(bye)
            c64.CHROUT(bye[c])


        float clock_seconds = ((c64.TIME_LO as float) + 256.0*(c64.TIME_MID as float) + 65536.0*(c64.TIME_HI as float)) / 60
        ubyte hours = clock_seconds / 3600 as ubyte
        clock_seconds -= hours * 3600
        ubyte minutes = clock_seconds / 60 as ubyte
        clock_seconds -= minutes * 60
        ubyte seconds = 0; clock_seconds as ubyte  ; @todo fix crash


    ; @todo implement strcpy/strcat/strlen?
        c64scr.print("system time is ")
        c64scr.print_ub(hours)
        c64.CHROUT(':')
        c64scr.print_ub(minutes)
        c64.CHROUT(':')
        c64scr.print_ub(seconds)
        c64.CHROUT('\n')
    }

}
