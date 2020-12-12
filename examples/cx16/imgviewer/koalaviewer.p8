%target cx16
%import graphics
%import textio
%import diskio
%import c64colors

main {
    const uword load_location = $6000

    sub start() {
        graphics.enable_bitmap_mode()
        ; set a better C64 color palette, the Cx16's default is too saturated
        c64colors.set_palette_pepto()

        if strlen(diskio.status(8))     ; trick to check if we're running on sdcard or host system shared folder
            show_pics_sdcard()
        else
            show_file_list()

        repeat {
            ;
        }
    }

    sub show_file_list() {
        ; listing a directory doesn't work with a shared host directory in the emulator...
        str[] pictures = [
            "i01-blubb-sphinx.koa",
            "i02-bugjam-jsl.koa",
            "i03-dinothawr-ar.koa",
            "i04-fox-leon.koa",
            "i05-hunter-agod.koa",
            "i06-jazzman-jds.koa",
            "i07-katakis-jegg.koa"
        ]

        uword nameptr
        for nameptr in pictures {
            uword size = diskio.load(8, nameptr, load_location)
            if size==10001 {
                convert_koalapic()
            } else {
                txt.print_uw(size)
                txt.print("\nload error\n")
                txt.print(diskio.status(8))
            }
            load_image_from_disk(nameptr)
            cx16.wait(60)
        }
    }

    sub show_pics_sdcard() {

        ; load and show all *.koa pictures on the disk.
        ; this only works in the emulator V38 with an sd-card image with the files on it.

        str[20] filename_ptrs
        ubyte num_files = diskio.list_files(8, ".koa", true, &filename_ptrs, len(filename_ptrs))
        if num_files {
            while num_files {
                num_files--
                load_image_from_disk(filename_ptrs[num_files])
                cx16.wait(60)
            }
        } else {
            txt.print("no *.koa files found\n")
        }
    }

    sub load_image_from_disk(uword filenameptr) {
        ; special load routine that uses per-byte loading so it works from an sd-card image
        if diskio.f_open(8, filenameptr) {
            uword size = diskio.f_read(load_location, 2)    ; skip the first 2 bytes (load address)
            if size==2 {
                size = diskio.f_read(load_location, 10001)
                if size == 10001 {
                    convert_koalapic()
                } else {
                    txt.print_uw(size)
                    txt.print("\nload error\n")
                    txt.print(diskio.status(8))
                }
            }
            diskio.f_close()
        }
    }

    sub convert_koalapic() {
        ubyte cy
        ubyte @zp cx
        uword @zp cy_times_forty = 0
        ubyte @zp d
        uword bitmap_ptr = load_location

        ; theoretically you could put the 8-pixel array in zeropage to squeeze out another tiny bit of performance
        ubyte[8] pixels

        for cy in 0 to 24*8 step 8 {
            for cx in 0 to 39 {
                for d in 0 to 7 {
                    cx16.r0 = cx as uword * 8
                    cx16.r1 = cy as uword + d
                    cx16.FB_cursor_position()
                    get_8_pixels()
                    cx16.r0 = &pixels
                    cx16.r1 = 8
                    cx16.FB_set_pixels()
                }
            }
            cy_times_forty += 40
        }

        sub get_8_pixels() {
            ubyte  bm = @(bitmap_ptr)
            ubyte  @zp  m = mcol(bm)
            pixels[7] = m
            pixels[6] = m
            bm >>= 2
            m = mcol(bm)
            pixels[5] = m
            pixels[4] = m
            bm >>= 2
            m = mcol(bm)
            pixels[3] = m
            pixels[2] = m
            bm >>= 2
            m = mcol(bm)
            pixels[1] = m
            pixels[0] = m
            bitmap_ptr++

            sub mcol(ubyte b) -> ubyte {
                ubyte @zp color
                when b & 3 {
                    0 -> color = @(load_location + 8000 + 1000 + 1000) & 15
                    1 -> color = @(load_location + 8000 + cy_times_forty + cx) >>4
                    2 -> color = @(load_location + 8000 + cy_times_forty + cx) & 15
                    else -> color = @(load_location + 8000 + 1000 + cy_times_forty + cx) & 15
                }
                return color
            }
        }
    }
}
