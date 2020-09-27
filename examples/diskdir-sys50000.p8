%target c64
%import textio
%import syslib
%zeropage basicsafe
%option no_sysinit
%launcher none
%address 50000

; This example shows the directory contents of disk drive 8.
; You load it with  LOAD "diskdir-sys50000",8,1
; and then call it with SYS 50000.

main {
    sub start() {
        txt.print("directory of disk drive #8:\n\n")
        diskdir(8)
    }

    sub diskdir(ubyte drivenumber) {
        c64.SETNAM(1, "$")
        c64.SETLFS(1, drivenumber, 0)
        c64.OPEN()          ; open 1,8,0,"$"
        c64.CHKIN(1)        ; use #1 as input channel

        repeat 4 {
            void c64.CHRIN()     ; skip the 4 prologue bytes
        }

        ; while not key pressed / EOF encountered, read data.
        while not (@($c6) | c64.STATUS) {
            txt.print_uw(mkword(c64.CHRIN(), c64.CHRIN()))
            txt.chrout(' ')
            ubyte @zp char
            do {
                char = c64.CHRIN()
                txt.chrout(char)
            } until char==0
            txt.chrout('\n')
            repeat 2 {
                void c64.CHRIN()     ; skip 2 bytes
            }
        }

        c64.CLOSE(1)
        c64.CLRCHN()        ; restore default i/o devices
    }
}
