%import textio
%option no_sysinit
%zeropage basicsafe

main {
    romsub @rombank 10  $C09F = audio_init()
    romsub @rambank 22  $A000 = hiram_routine()

    sub start() {
        audio_init()
        hiram_routine()
    }
}
