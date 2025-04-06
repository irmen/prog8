%import textio
%import diskio

%option no_sysinit
%zeropage basicsafe

main {
    extsub @bank 4   $A000 = lib_routine1(ubyte value @A) clobbers(X) -> uword @AY
    extsub @bank 5   $A000 = lib_routine2(ubyte value @A) clobbers(X) -> uword @AY
    extsub @bank 10  $C09F = audio_init() -> bool @A

    sub start() {

        ; load the example libraries in hiram banks 4 and 5
        ; in this example these are constants, but you can also specify
        ; a variable for the bank so you can vary the bank where the routine is loaded.
        cx16.push_rambank(4)        ; remember the original ram bank, switch to 4
        void diskio.load("library1.prg", $a000)
        cx16.rambank(5)
        void diskio.load("library2.prg", $a000)

        cx16.pop_rambank()      ; restore orig rambank.

        ; call a routine from the Audio rom bank:
        bool success = audio_init()

        ; call hiram banked loaded routines:
        cx16.r0 = lib_routine1(11)
        txt.print_uw(cx16.r0)
        txt.nl()

        cx16.r0 = lib_routine2(99)
        txt.print_uw(cx16.r0)
        txt.nl()
    }
}
