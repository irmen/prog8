%import c64lib
%import c64utils

~ main {

sub start() {

    ; set screen colors and activate lowercase charset
    c64.EXTCOL = 5
    c64.BGCOL0 = 0
    c64.COLOR = 1
    c64.VMCSB = %10111

    ; use kernel routine to write text (sorry, loops don't work in asm yet)
    c64.CHROUT('H')
    c64.CHROUT('e')
    c64.CHROUT('l')
    c64.CHROUT('l')
    c64.CHROUT('o')
    c64.CHROUT('!')
    c64.CHROUT('\n')

    return
}

}
