%target cx16
%import gfx2
%import textio
%import diskio
%import koala_module
%import iff_module
%import pcx_module
%import bmp_module
;; %import ci_module
%zeropage basicsafe


main {
    sub start() {
        ; trick to check if we're running on sdcard or host system shared folder
        txt.print("\nimage viewer for commander x16\nformats supported: .iff, .pcx, .bmp, .koa (c64 koala)\n\n")
        if strlen(diskio.status(8)) {
            txt.print("enter image file name or just enter for all on disk: ")
            ubyte i = txt.input_chars(diskio.filename)
            gfx2.screen_mode(1)    ; 320*240, 256c
            if i
                attempt_load(diskio.filename)
            else
                show_pics_sdcard()

            ; txt.print("\nnothing more to do.\n")
        }
        else
            txt.print("files are read with sequential file loading.\nin the emulator this currently only works with files on an sd-card image.\nsorry :(\n")

        gfx2.screen_mode(255)      ; back to default text mode and palette
        txt.print("that was all folks!\n")
    }

    sub show_pics_sdcard() {

        ; load and show the images on the disk with the given extensions.
        ; this only works in the emulator V38 with an sd-card image with the files on it.

        str[40] filename_ptrs
        ubyte num_files = diskio.list_files(8, 0, false, &filename_ptrs, len(filename_ptrs))
        if num_files {
            while num_files {
                num_files--
                attempt_load(filename_ptrs[num_files])
            }
        } else
            txt.print("no files in directory!\n")

    }

    sub attempt_load(uword filenameptr) {
        ;txt.print(">> ")
        ;txt.print(filenameptr)
        ;txt.chrout('\n')
        uword extension = filenameptr + rfind(filenameptr, '.')
        if strcmp(extension, ".iff")==0 {
            ;txt.print("loading ")
            ;txt.print("iff\n")
            if iff_module.show_image(filenameptr) {
                if iff_module.num_cycles {
                    repeat 500 {
                        cx16.wait(1)
                        iff_module.cycle_colors_each_jiffy()
                    }
                }
                else
                    cx16.wait(180)
            } else {
                load_error(filenameptr)
            }
        }
        else if strcmp(extension, ".pcx")==0 {
            ;txt.print("loading ")
            ;txt.print("pcx\n")
            if pcx_module.show_image(filenameptr) {
                cx16.wait(180)
            } else {
                load_error(filenameptr)
            }
        }
        else if strcmp(extension, ".koa")==0 {
            ;txt.print("loading ")
            ;txt.print("koala\n")
            if koala_module.show_image(filenameptr) {
                cx16.wait(180)
            } else {
                load_error(filenameptr)
            }
        }
        else if strcmp(extension, ".bmp")==0 {
            ;txt.print("loading ")
            ;txt.print("bmp\n")
            if bmp_module.show_image(filenameptr) {
                cx16.wait(180)
            } else {
                load_error(filenameptr)
            }
        }
;        else if strcmp(extension, ".ci")==0 {
;;            txt.print("loading ")
;;            txt.print("ci\n")
;            if ci_module.show_image(filenameptr) {
;                cx16.wait(180)
;            } else {
;                load_error(filenameptr)
;            }
;        }
    }

    sub load_error(uword filenameptr) {
        gfx2.screen_mode(255)      ; back to default text mode and palette
        txt.print(filenameptr)
        txt.print(": load error\n")
        exit(1)
    }

    sub extension_equals(uword stringptr, uword extensionptr) -> ubyte {
        ubyte ix = rfind(stringptr, '.')
        return ix<255 and strcmp(stringptr+ix, extensionptr)==0
    }

    sub rfind(uword stringptr, ubyte char) -> ubyte {
        ubyte i
        for i in strlen(stringptr)-1 downto 0 {
            if @(stringptr+i)==char
                return i
        }
        return 0
    }

}

custompalette {

    sub set_bgra(uword palletteptr, uword num_colors) {
        uword vera_palette_ptr = $fa00
        ubyte red
        ubyte greenblue

        ; 4 bytes per color entry (BGRA), adjust color depth from 8 to 4 bits per channel.
        repeat num_colors {
            red = @(palletteptr+2) >> 4
            greenblue = @(palletteptr+1) & %11110000
            greenblue |= @(palletteptr+0) >> 4    ; add Blue
            palletteptr+=4
            cx16.vpoke(1, vera_palette_ptr, greenblue)
            vera_palette_ptr++
            cx16.vpoke(1, vera_palette_ptr, red)
            vera_palette_ptr++
        }
    }

    sub set_grayscale256() {
        ; grays $000- $fff stretched out over all the 256 colors
        ubyte c = 0
        uword vera_palette_ptr = $fa00
        repeat 16 {
            repeat 16 {
                cx16.vpoke(1, vera_palette_ptr, c)
                vera_palette_ptr++
                cx16.vpoke(1, vera_palette_ptr, c)
                vera_palette_ptr++
            }
            c += $11
        }
    }
}
