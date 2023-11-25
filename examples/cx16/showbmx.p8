; Viewer program for BMX image files.
; This program shows *one* way to do it, by checking the header upfront,
; and loading the palette into system ram first. The simplest way to load
; a BMX file is to just read everything into vram directly using a single bmx.load() call.
;
; BMX file format: see https://cx16forum.com/forum/viewtopic.php?t=6945

%import textio
%import palette
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
                    txt.print("\nsize: ")
                    txt.print_uw(bmx.width)
                    txt.chrout('*')
                    txt.print_uw(bmx.height)
                    txt.print("  bpp: ")
                    txt.print_uw(bmx.bitsperpixel)
                    txt.nl()
                    sys.wait(100)

                    ; tell the loader to put the palette into system memory instead
                    ; also make palette black at first, to hide loading (even though it is very fast)
                    ; (you could do a soft fade-in effect with this for instance)
                    bmx.palette_buffer_ptr = memory("palette", 512, 0)
                    sys.memset(bmx.palette_buffer_ptr, 512, 0)
                    palette.set_rgb(bmx.palette_buffer_ptr, 256)

                    ; switch to correct screen mode and color depth
                    void cx16.screen_mode($80, false)
                    cx16.VERA_L0_CONFIG = cx16.VERA_L0_CONFIG & %11111100 | bmx.vera_colordepth

                    ; now load the image
                    if bmx.load(8, filename, 0, 0, 320) {
                        activate_palette()
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

    sub activate_palette() {
        ; copies the pallette data from the memory buffer into vram
        cx16.r1 = bmx.palette_buffer_ptr
        cx16.r2L = bmx.palette_start
        cx16.r3L = bmx.palette_entries
        do {
            palette.set_color(cx16.r2L, peekw(cx16.r1))
            cx16.r1+=2
            cx16.r2L++
            cx16.r3L--
        } until cx16.r3L==0
    }
}
