%import syslib
%import textio
%import floats
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

; TODO why has the prg become bigger since register args?


main {

    sub start() {

        txt.lowercase()

        ; use optimized routine to write text
        txt.print("Hello!\n")

        ; use iteration to write text
        str question = "How are you?\n"
        ubyte char
        for char in question
            txt.chrout(char)

        ; use indexed loop to write characters
        str bye = "Goodbye!\n"
        for char in 0 to len(bye)-1
            txt.chrout(bye[char])

        ubyte time_lo
        ubyte time_mid
        ubyte time_hi

        %asm {{
            stx  P8ZP_SCRATCH_REG
            jsr  c64.RDTIM      ; A/X/Y
            sta  time_lo
            stx  time_mid
            sty  time_hi
            ldx  P8ZP_SCRATCH_REG
        }}

        float clock_seconds = ((mkword(time_mid, time_lo) as float) + (time_hi as float)*65536.0) / 60
        float hours = floor(clock_seconds / 3600)
        clock_seconds -= hours*3600
        float minutes = floor(clock_seconds / 60)
        clock_seconds = floor(clock_seconds - minutes * 60.0)

        txt.print("system time (jiffy clock) is ")
        floats.print_f(hours)
        txt.chrout(':')
        floats.print_f(minutes)
        txt.chrout(':')
        floats.print_f(clock_seconds)
        txt.chrout('\n')

        txt.print("bye!\n")
    }
}
