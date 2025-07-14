%option no_sysinit
%zeropage basicsafe
%import textio

main {
    sub start() {
        ubyte @shared index = 3
        ubyte[10] array
        alias curframe = array

        cx16.r0 = &curframe
        cx16.r1 = &curframe[3]
        cx16.r2 = &curframe + 3
        cx16.r3 = &curframe[index]
        cx16.r4 = &curframe + index

        txt.print_uw(cx16.r0)
        txt.nl()
        txt.print_uw(cx16.r1)
        txt.spc()
        txt.print_uw(cx16.r2)
        txt.nl()
        txt.print_uw(cx16.r3)
        txt.spc()
        txt.print_uw(cx16.r4)
        txt.nl()
    }
}

; code sizes on 11.5:  6502: $20a  virt: 140 instr
; code sizes on 12.0:  6502: ????  virt: ??? instr  (BORKED!)
