%import syslib
%import textio
%import floats
%zeropage basicsafe

; TODO use RDTIM() to get the time and make this system agnostic

main {

    sub start() {

        txt.lowercase()

        ; use optimized routine to write text
        txt.print("Hello!\n")

        ; use iteration to write text
        str question = "How are you?\n"
        ubyte char
        for char in question
            c64.CHROUT(char)

        ; use indexed loop to write characters
        str bye = "Goodbye!\n"
        for char in 0 to len(bye)-1
            c64.CHROUT(bye[char])


        float clock_seconds = ((mkword(c64.TIME_MID, c64.TIME_LO) as float) + (c64.TIME_HI as float)*65536.0) / 60
        float hours = floor(clock_seconds / 3600)
        clock_seconds -= hours*3600
        float minutes = floor(clock_seconds / 60)
        clock_seconds = floor(clock_seconds - minutes * 60.0)

        txt.print("system time in ti$ is ")
        floats.print_f(hours)
        c64.CHROUT(':')
        floats.print_f(minutes)
        c64.CHROUT(':')
        floats.print_f(clock_seconds)
        c64.CHROUT('\n')

        txt.print("bye!\n")
    }
}
