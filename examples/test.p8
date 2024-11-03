%import textio
%option no_sysinit
%zeropage basicsafe

main {
    romsub @bank 10  $C09F = audio_init()
    romsub @bank 5  $A000 = hiram_routine()

    sub start() {
        ; put an rts in hiram bank 5 to not crash
        cx16.rambank(5)
        @($a000) = $60
        cx16.rambank(0)

        audio_init()
        hiram_routine()
    }
}
