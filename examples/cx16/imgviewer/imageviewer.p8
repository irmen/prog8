%target cx16
%import graphics
%import textio
%import diskio
%import koala_module
%import iff_module
%import pcx_module
%import bmp_module
%import ci_module

main {
    sub start() {
        graphics.enable_bitmap_mode()

        if strlen(diskio.status(8))     ; trick to check if we're running on sdcard or host system shared folder
            show_pics_sdcard()
        else {
            txt.print("only works with files on sdcard image!\n(because of emulator restrictions)")
        }

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
            txt.print("\nthat was the last file.\n")
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
            void iff_module.show_image(filenameptr)
            txt.clear_screen()
            cx16.wait(120)
        }
        else if strcmp(extension, ".pcx")==0 {
            txt.print("loading ")
            txt.print("pcx\n")
            void pcx_module.show_image(filenameptr)
            txt.clear_screen()
            cx16.wait(120)
        }
        else if strcmp(extension, ".koa")==0 {
            txt.print("loading ")
            txt.print("koala\n")
            void koala_module.show_image(filenameptr)
            txt.clear_screen()
            cx16.wait(120)
        }
        else if strcmp(extension, ".bmp")==0 {
            txt.print("loading ")
            txt.print("bmp\n")
            void bmp_module.show_image(filenameptr)
            txt.clear_screen()
            cx16.wait(120)
        }
        else if strcmp(extension, ".ci")==0 {
            txt.print("loading ")
            txt.print("ci\n")
            void ci_module.show_image(filenameptr)
            txt.clear_screen()
            cx16.wait(120)
        }
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
