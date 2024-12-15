; Viewer program for BMX image files.
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
            ;; diskio.fastmode(1)
            txt.print("\nenter bmx image filename: ")
            if txt.input_chars(&filename)!=0 {

                if bmx.open(8, filename) {
                    txt.print("\nsize: ")
                    txt.print_uw(bmx.width)
                    txt.chrout('*')
                    txt.print_uw(bmx.height)
                    txt.print("  bpp: ")
                    txt.print_uw(bmx.bitsperpixel)
                    txt.print("  num colors: ")
                    txt.print_uw(bmx.palette_entries)
                    txt.nl()
                    sys.wait(100)

                    ; tell the loader to put the palette into system memory instead
                    ; also make palette black at first, to hide loading (even though it is very fast)
                    ; (you could do a soft fade-in effect with this for instance)
                    bmx.palette_buffer_ptr = memory("palette", 512, 0)
                    sys.memset(bmx.palette_buffer_ptr, 512, 0)
                    palette.set_rgb(bmx.palette_buffer_ptr, 256, 0)

                    ; switch to bitmap screen mode and color depth:  320*240
                    void cx16.screen_mode($80, false)       ; we're lazy and just use a kernal routine to set up the basics
                    cx16.VERA_L0_CONFIG = cx16.VERA_L0_CONFIG & %11111100 | bmx.vera_colordepth

                    ; now load the image
                    if bmx.width==320 {
                        ; can use the fast, full-screen load routine
                        if bmx.continue_load(0, 0) {
                            if bmx.height<240 {
                                ; fill the remaining bottom part of the screen
                                cx16.GRAPH_set_colors(bmx.border, bmx.border, 99)
                                cx16.GRAPH_draw_rect(0, bmx.height, 320, 240-bmx.height, 0, true)
                            }
                            activate_palette()
                            void txt.waitkey()
                        }
                    } else {
                        ; clear the screen with the border color
                        cx16.GRAPH_set_colors(0, 0, bmx.border)
                        cx16.GRAPH_clear()
                        ; need to use the slower load routine that does padding
                        ; center the image on the screen nicely
                        uword offset = (320-bmx.width)/2 + (240-bmx.height)/2*320
                        when(bmx.bitsperpixel) {
                            1 -> offset /= 8
                            2 -> offset /= 4
                            4 -> offset /= 2
                            else -> {}
                        }
                        if bmx.continue_load_stamp(0, offset, 320) {
                            activate_palette()
                            void txt.waitkey()
                        }
                    }
                }

                cbm.CINT()  ; reset screen

                if bmx.error_message!=0 {
                    txt.print("load error:\n")
                    txt.print(bmx.error_message)
                    txt.nl()
                    sys.wait(120)
                }
            }
        }
    }

    sub activate_palette() {
        ; copies the palette data from the memory buffer into vram
        cx16.VERA_DC_BORDER = bmx.border
        cx16.r4 = bmx.palette_buffer_ptr
        cx16.r5L = bmx.palette_start
        cx16.r6L = lsb(bmx.palette_entries)
        do {
            palette.set_color(cx16.r5L, peekw(cx16.r4))
            cx16.r4+=2
            cx16.r5L++
            cx16.r6L--
        } until cx16.r6L==0
    }
}
