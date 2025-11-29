%option no_sysinit ; leave the CX16 defaults in place
%zeropage basicsafe ; don't step on BASIC zero page locations
; %import textio


main {
    sub start() {
        uword ptr1 = memory("buffer1", 100, 0)
        const uword ptr2 = memory("buffer2",100,0)

        ptr1++
        ptr1 = ptr2
;        txt.print_uw(ptr1)
;        txt.nl()
;        txt.print_uw(ptr2)
;        txt.nl()
    }
}
