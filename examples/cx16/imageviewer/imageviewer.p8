%target cx16
%import graphics
%import textio
%import diskio
%import koala_module
%import iff_module
%import pcx_module
%import bmp_module
;; %import ci_module

; TODO WHY is this 400 bytes larger than a few hours ago?

main {
    sub start() {
        ; trick to check if we're running on sdcard or host system shared folder
        txt.print("\nimage viewer for commander x16\nformats supported: .iff, .pcx, .bmp, .koa (c64 koala)\n\n")
        if strlen(diskio.status(8)) {
            txt.print("enter image file name or just enter for all on disk: ")
            ubyte i = txt.input_chars(diskio.filename)
            graphics.enable_bitmap_mode()
            if i
                attempt_load(diskio.filename)
            else
                show_pics_sdcard()

            txt.print("\nnothing more to do.\n")
        }
        else
            txt.print("files are read with sequential file loading.\nin the emulator this currently only works with files on an sd-card image.\nsorry :(\n")

        repeat {
            ;
        }
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
        txt.print(">> ")
        txt.print(filenameptr)
        txt.chrout('\n')
        uword extension = filenameptr + rfind(filenameptr, '.')
        if strcmp(extension, ".iff")==0 {
            txt.print("loading ")
            txt.print("iff\n")
            if iff_module.show_image(filenameptr)
                txt.clear_screen()
            else
                txt.print("load error!\n")
            cx16.wait(120)
        }
        else if strcmp(extension, ".pcx")==0 {
            txt.print("loading ")
            txt.print("pcx\n")
            if pcx_module.show_image(filenameptr)
                txt.clear_screen()
            else
                txt.print("load error!\n")
            cx16.wait(120)
        }
        else if strcmp(extension, ".koa")==0 {
            txt.print("loading ")
            txt.print("koala\n")
            if koala_module.show_image(filenameptr)
                txt.clear_screen()
            else
                txt.print("load error!\n")
            cx16.wait(120)
        }
        else if strcmp(extension, ".bmp")==0 {
            txt.print("loading ")
            txt.print("bmp\n")
            if bmp_module.show_image(filenameptr)
                txt.clear_screen()
            else
                txt.print("load error!\n")
            cx16.wait(120)
        }
;        else if strcmp(extension, ".ci")==0 {
;            txt.print("loading ")
;            txt.print("ci\n")
;            if ci_module.show_image(filenameptr)
;                txt.clear_screen()
;            else
;                txt.print("load error!\n")
;            cx16.wait(120)
;        }
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

palette {

    sub set_rgb4(uword palletteptr, uword num_colors) {
        ; 2 bytes per color entry, the Vera uses this, but the R/GB bytes order is swapped
        uword vera_palette_ptr = $fa00
        repeat num_colors {
            cx16.vpoke(1, vera_palette_ptr+1, @(palletteptr))
            palletteptr++
            cx16.vpoke(1, vera_palette_ptr, @(palletteptr))
            palletteptr++
            vera_palette_ptr+=2
        }
    }

    sub set_rgb8(uword palletteptr, uword num_colors) {
        ; 3 bytes per color entry, adjust color depth from 8 to 4 bits per channel.
        uword vera_palette_ptr = $fa00
        ubyte red
        ubyte greenblue
        repeat num_colors {
            red = @(palletteptr) >> 4
            palletteptr++
            greenblue = @(palletteptr) & %11110000
            palletteptr++
            greenblue |= @(palletteptr) >> 4    ; add Blue
            palletteptr++
            cx16.vpoke(1, vera_palette_ptr, greenblue)
            vera_palette_ptr++
            cx16.vpoke(1, vera_palette_ptr, red)
            vera_palette_ptr++
        }
    }

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

    sub set_monochrome() {
        uword vera_palette_ptr = $fa00
        cx16.vpoke(1, vera_palette_ptr, 0)
        vera_palette_ptr++
        cx16.vpoke(1, vera_palette_ptr, 0)
        vera_palette_ptr++
        repeat 255 {
            cx16.vpoke(1, vera_palette_ptr, 255)
            vera_palette_ptr++
            cx16.vpoke(1, vera_palette_ptr, 255)
            vera_palette_ptr++
        }
    }

    sub set_grayscale() {
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
