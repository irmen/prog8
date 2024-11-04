%import textio
; %import floats
%zeropage basicsafe

main {
    sub start() {
        txt.print("current banks: ")
        txt.print_ubbin(c64.getbanks(), true)
        txt.print("\nmemtop: ")
        txt.print_uwhex(cbm.MEMTOP(0, true), true)
        txt.print("\n8 bytes at $a000:\n")
        for cx16.r0 in $a000 to $a007 {
            txt.print_ubhex(@(cx16.r0), false)
            txt.spc()
        }
        txt.print("\nwriting data to there, result:\n")
        @($a000) = $11
        @($a001) = $22
        @($a002) = $33
        @($a003) = $44
        @($a004) = $55
        @($a005) = $66
        @($a006) = $77
        @($a007) = $88

        for cx16.r0 in $a000 to $a007 {
            txt.print_ubhex(@(cx16.r0), false)
            txt.spc()
        }
        txt.nl()

;        txt.print("floating point calc: ")
;        float @shared f1 = 1.0
;        float @shared f2 = 7.0
;        floats.print(f1/f2)
;        txt.nl()
    }
}
