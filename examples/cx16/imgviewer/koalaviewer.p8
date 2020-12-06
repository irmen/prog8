%target cx16
%import graphics
%import textio
%import diskio
%import c64colors

main {
    const uword load_location = $4000

    sub start() {

        str[] pictures = [
            "i01-blubb-sphinx.bin",
            "i02-bugjam-jsl.bin",
            "i03-dinothawr-ar.bin",
            "i04-fox-leon.bin",
            "i05-hunter-agod.bin",
            "i06-jazzman-jds.bin",
            "i07-katakis-jegg.bin"
        ]

        ; set a better C64 color palette, the Cx16's default is too saturated
        c64colors.set_palette_pepto()
        graphics.enable_bitmap_mode()
        repeat {
            ubyte file_idx
            for file_idx in 0 to len(pictures)-1 {
                load_image(pictures[file_idx])
                wait()
            }
        }
    }

    sub wait() {
        uword jiffies = 0
        c64.SETTIM(0,0,0)

        while jiffies < 180 {
            ; read clock
            %asm {{
                stx  P8ZP_SCRATCH_REG
                jsr  c64.RDTIM
                sta  jiffies
                stx  jiffies+1
                ldx  P8ZP_SCRATCH_REG
            }}
        }
    }

    sub load_image(uword filenameptr) {
        uword length = diskio.load(8, filenameptr, load_location)

        if length != 10001 {
            txt.print_uw(length)
            txt.print("\nload error\n")
            diskio.status(8)
            exit(1)
        }
        convert_koalapic()
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
