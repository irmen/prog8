%import textio
%import cx16diskio
%import palette
%zeropage basicsafe
%zpreserved $22,$28     ; zsound lib uses this region


;; Proof Of Concept ZSM player using a binary blob version of zsound library by ZeroByte relocated to something usable here.

; "issues":
; - prog8 (or rather, 64tass) cannot "link" other assembly object files so we have to incbin a binary blob.
; - zsound player ZP usage is only known after compilation of zsound lib. And then this has to be blocked off in the prog8 program.
; - zsound lib BSS section has to be expanded into real zeros as part of the included binary.
; - zsound binary starts with 2 byte prg header that shifts the jump table 2 bytes (not a big issue but a bit untidy)
; - prog8 always sticks main module at the beginning so can't create a container module to stick zsound in (if you want to load it at the beginning)
; - prog8 always has some bootstrap code after the basic launcher so we can only start loading stuff after that
; - prog8 main has to be set to a fixed address (in this case $0830) to which the special zsound binary has been compiled as well.


main $0830 {

zsound_lib:
    ; this has to be the first statement to make sure it loads at the specified module address $0830
    %asmbinary "zsmplayer-0830.bin"

    ; note: jump table is offset by 2 from the load address (because of prg header)
    romsub $0832 = zsm_init() clobbers(A)
    romsub $0835 = zsm_play() clobbers(A, X, Y)
    romsub $0838 = zsm_playIRQ() clobbers(A, X, Y)
    romsub $083b = zsm_start(ubyte bank @A, uword song_address @XY) clobbers(A, X, Y) -> ubyte @Pc
    romsub $083e = zsm_stop()
    romsub $0841 = zsm_setspeed(uword hz @XY) clobbers(A, X, Y)
    romsub $0844 = zsm_setloop(ubyte count @A)
    romsub $0847 = zsm_forceloop(ubyte count @A)
    romsub $084a = zsm_noloop()
    romsub $084d = zsm_setcallback(uword address @XY)
    romsub $0850 = zsm_clearcallback() clobbers(A)
    romsub $0853 = zsm_get_music_speed() clobbers(A) -> uword @XY

    const ubyte song_bank = 4
    const uword song_address = $a000

    sub start() {
        txt.print("zsound demo program!\n")

        if not cx16diskio.load_raw(8, "colony.zsm", song_bank, song_address) {
            txt.print("?can't load song\n")
            return
        }
        cx16.rambank(song_bank)

        zsm_init()
        zsm_setcallback(&end_of_song_cb)
        if zsm_start(song_bank, song_address)==0 {
            txt.print("music speed: ")
            txt.print_uw(zsm_get_music_speed())
            txt.print(" hz\nplaying song! hit enter to stop.\n")

            ; for IRQ based playback instead:  cx16.set_irq(&zsm_playIRQ, true)
            while cx16.joystick_get2(0)==$ffff {
                sys.waitvsync()
                repeat 800 {
                    ; artificially delay calling the play routine so we can see its raster time
                    %asm {{
                        nop
                    }}
                }
                palette.set_color(0, $0c5)
                zsm_play()
                palette.set_color(0, $000)
            }
            zsm_stop()
        } else {
            txt.print("?song start error\n")
        }
    }

    sub end_of_song_cb() {
        txt.print("end of song!\n")
    }
}
