
; viewer program for BMX image files.
; see https://cx16forum.com/forum/viewtopic.php?t=6945

%import textio
%import bmx
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        str filename = "?"*40

        repeat {
            txt.print("\nenter bmx image filename: ")
            if txt.input_chars(&filename) {
                if bmx.load_header(8, filename) {
                    txt.print("\nwidth: ")
                    txt.print_uw(bmx.width)
                    txt.print("\nheight: ")
                    txt.print_uw(bmx.height)
                    txt.print("\nbpp: ")
                    txt.print_uw(bmx.bitsperpixel)
                    txt.nl()
                    sys.wait(100)

                    ; switch to correct screen mode and color depth
                    void cx16.screen_mode($80, false)
                    cx16.VERA_L0_CONFIG = cx16.VERA_L0_CONFIG & %11111100 | bmx.vera_colordepth
                    ; actually load
                    if bmx.load(8, filename, 0, 0, 320) {
                        void txt.waitkey()
                    }
                }

                cbm.CINT()  ; reset screen

                if bmx.error_message {
                    txt.print("load error:\n")
                    txt.print(bmx.error_message)
                    txt.nl()
                    sys.wait(120)
                }
            }
        }
    }
}
