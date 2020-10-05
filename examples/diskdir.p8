%import textio
%import syslib
%option no_sysinit
%zeropage basicsafe

; This example shows the directory contents of disk drive 8.
; Note: this program is compatible with C64 and CX16.    TODO not yet on cx16

main {
    sub start() {
        txt.print("directory of disk drive #8:\n\n")
        diskdir(8)
    }

    sub diskdir(ubyte drivenumber) {
        c64.SETNAM(1, "$")
        c64.SETLFS(1, drivenumber, 0)
        void c64.OPEN()          ; open 1,8,0,"$"           ; TODO handle error condition in carry/A
        void c64.CHKIN(1)        ; use #1 as input channel  ; TODO handle error condition in carry/A

        repeat 4 {
            void c64.CHRIN()     ; skip the 4 prologue bytes
        }

        ; while not key pressed / EOF encountered, read data.
        ubyte status = c64.READST()
        while not (@($c6) | status) {       ; TODO replace $c6 by kernal function c64.STOP() once the multi-return and status flags thingy work
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
            status = c64.READST()
        }

        c64.CLOSE(1)
        c64.CLRCHN()        ; restore default i/o devices

        if status and status != 64 {            ; 64=end of file
            txt.print("\ni/o error, status: ")
            txt.print_ub(status)
            txt.chrout('\n')
        }

    }
}
