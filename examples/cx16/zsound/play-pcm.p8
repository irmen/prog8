%import textio
%import cx16diskio
%import palette
%zeropage basicsafe
%zpreserved $22,$26     ; zsound lib uses this region


;; Proof Of Concept ZCM player using a binary blob version of zsound library by ZeroByte relocated to something usable here.

; "issues": see play-zsm.p8


main $0830 {

zsound_lib:
    ; this has to be the first statement to make sure it loads at the specified module address $0830
    %asmbinary "pcmplayer-0830.bin"

    ; note: jump table is offset by 2 from the load address (because of prg header)
    romsub $0832 = pcm_init() clobbers(A)
    romsub $0835 = pcm_trigger_digi(ubyte bank @A, uword song_address @XY)
    romsub $0838 = pcm_play() clobbers(A, X, Y)
    romsub $083b = pcm_stop() clobbers(A)
    romsub $083e = pcm_set_volume(ubyte volume @A)

    const ubyte digi_bank = 1
    const uword digi_address = $a000
    const ubyte zcm_DIGITAB_size = 8        ; header size

    sub start() {
        txt.print("zsound pcm digi demo program!\n")

        c64.SETMSG(%10000000)       ; enable kernal status messages for load
        if not cx16diskio.load_raw(8, "terminator2.zcm", digi_bank, digi_address) {
            txt.print("?can't load\n")
            return
        }
        c64.SETMSG(0)

        cx16.rambank(digi_bank)
        ; initialize header pointer of the zcm to point to actual sample data
        poke(digi_address+2, digi_bank)
        pokew(digi_address, digi_address+zcm_DIGITAB_size)

        pcm_init()
        txt.print("\nplaying digi! hit enter to stop.\n")
        pcm_trigger_digi(digi_bank, digi_address)
        while cx16.joystick_get2(0)==$ffff {
            sys.waitvsync()
            repeat 1000 {
                ; artificially delay calling the play routine so we can see its raster time
                %asm {{
                    nop
                }}
            }
            palette.set_color(0, $0c5)
            pcm_play()
            palette.set_color(0, $000)
        }
        pcm_stop()
    }
}
