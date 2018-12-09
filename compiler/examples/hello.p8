%import c64lib
%import c64utils

~ main {

sub start() {

    ; set screen colors and activate lowercase charset
    c64.EXTCOL = 5
    c64.BGCOL0 = 0
    c64.COLOR = 1
    c64.VMCSB |= 2

    ; use kernel routine to write text
    c64.STROUT("Hello!\n")

    str question = "How are you?\n"
    for ubyte c in 0 to len(question) {
        c64.CHROUT(question[c])
    }
    return
}

}
