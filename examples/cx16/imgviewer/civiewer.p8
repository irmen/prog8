%target cx16
%import graphics
%import textio
%import diskio
%option no_sysinit

; CommanderX16 Image file format.
; Numbers are encoded in little endian format (lsb first).
;
; offset      value
; -----------------
; HEADER (12 bytes):
; 0-1    'CI' in petscii , from "CommanderX16 Image".
; 2      Size of the header data following this byte (always 9, could become more if format changes)
; 3-4    Width in pixels  (must be multiple of 8)
; 5-6    Height in pixels
; 7      Bits-per-pixel  (1, 2, 4 or 8)  (= 2, 4, 16 or 256 colors)
;          this also determines the number of palette entries following later.
; 8      Settings bits.
;          bit 0 and 1 = compression.  00 = uncompressed
;                                      01 = RLE        [TODO not yet implemented]
;                                      10 = LZSA       [TODO not yet implemented]
;                                      11 = Exomizer   [TODO not yet implemented]
;          bit 2 = palette format.  0 = 4 bits/channel  (2 bytes per color, $0R $GB)  [TODO not yet implemented]
;                                   1 = 8 bits/channel  (3 bytes per color, $RR $GG $BB)
;                  4 bits per channel is what the Vera in the Cx16 supports.
;          bit 3 = bitmap format.   0 = raw bitmap pixels
;                                   1 = tile-based image   [TODO not yet implemented]
;          bit 4 = hscale (horizontal display resulution) 0 = 320 pixels, 1 = 640 pixels
;          bit 5 = vscale (vertical display resulution) 0 = 240 pixels, 1 = 480 pixels
;          bit 6,7: reserved, set to 0
; 9-11   Size of the bitmap data following the palette data.
;          This is a 24-bits number, can be 0 ("unknown", in which case just read until the end).
;
; PALETTE (always present but size varies):
; 12-... Color palette. Number of entries = 2 ^ bits-per-pixel.  Number of bytes per
;          entry is 2 or 3, depending on the chosen palette format in the setting bits.
;
; BITMAPDATA (size varies):
; After this, the actual image data follows.
; If the bitmap format is 'raw bitmap pixels', the bimap is simply written as a sequence
; of bytes making up the image's scan lines. #bytes per scan line = width * bits-per-pixel / 8
; If it is 'tiles', .... [TODO]
; If a compression scheme is used, the bitmap data here has to be decompressed first.
; TODO: with compressed files, store the data in compressed chunks of max 8kb uncompressed?
; (it is a problem to load let alone decompress a full bitmap at once because there will likely not be enough ram to do that)
; (doing it in chunks of 8 kb allows for sticking each chunk in one of the banked 8kb ram blocks, or even copy it directly to the screen)

main {

    ubyte[256] buffer
    ubyte[256] buffer2  ; add two more buffers to make enoughs space
    ubyte[256] buffer3  ;   to store a 256 color palette

    str filename = "trsi256.ci"
    const uword bitmap_load_address = $2000         ; TODO use progend() once it is available
    const uword max_bitmap_size = $9eff - bitmap_load_address

    sub start() {
        buffer[0] = 0
        buffer2[0] = 0
        buffer3[0] = 0
        ubyte read_success = false

        txt.print(filename)
        txt.chrout('\n')

        if(diskio.f_open(8, filename)) {
            uword size = diskio.f_read(buffer, 12)  ; read the header
            if size==12 {
                if buffer[0]=='c' and buffer[1]=='i' and buffer[2] == 9 {
                    if buffer[11] {
                        txt.print("file too large >64kb!\n")       ; TODO add support for large files
                    } else {
                        uword width = mkword(buffer[4], buffer[3])
                        uword height = mkword(buffer[6], buffer[5])
                        ubyte bpp = buffer[7]
                        uword num_colors = $0001 << bpp
                        ubyte flags = buffer[8]
                        ubyte compression = flags & %00000011
                        ubyte palette_format = (flags & %00000100) >> 2
                        ubyte bitmap_format = (flags & %00001000) >> 3
                        ; ubyte hscale = (flags & %00010000) >> 4
                        ; ubyte vscale = (flags & %00100000) >> 5
                        uword bitmap_size = mkword(buffer[10], buffer[9])
                        uword palette_size = num_colors*2
                        if palette_format
                            palette_size += num_colors  ; 3
                        txt.print_uw(width)
                        txt.chrout('*')
                        txt.print_uw(height)
                        txt.print(" * ")
                        txt.print_uw(num_colors)
                        txt.print(" colors\n")
                        if width > graphics.WIDTH {
                            txt.print("image is too wide for the display!\n")
                        } else if compression!=0 {
                            txt.print("compressed image not yet supported!\n")    ; TODO implement the various decompressions
                        } else if bitmap_format==1 {
                            txt.print("tiled bitmap not yet supported!\n")       ; TODO implement tiled image
                        } else if bitmap_size > max_bitmap_size {
                            ; TODO implement large file support by using memory banks  (nasty if compression is used though)
                            ; TODO in case of uncompressed data: do not read the full bitmap in memory but read a scanline at a time and display them as we go
                            txt.print("not enough ram to load bitmap!\nrequired: ")
                            txt.print_uw(bitmap_size)
                            txt.print(" available: ")
                            txt.print_uw(max_bitmap_size)
                            txt.chrout('\n')
                        } else {
                            txt.print("loading...")
                            size = diskio.f_read(buffer, palette_size)
                            if size==palette_size {
                                size = diskio.f_read(bitmap_load_address, bitmap_size)
                                if size==bitmap_size {
                                    ; all data has been loaded, display the image
                                    diskio.f_close()
                                    read_success = true
                                    txt.print("ok\n")
                                    ; restrict the height to what can be displayed using the graphics functions...
                                    if height > graphics.HEIGHT
                                        height = graphics.HEIGHT           ; TODO use maxv() once it is available
                                    graphics.enable_bitmap_mode()
                                    set_palette(num_colors, palette_format, buffer)
                                    when bpp {
                                        8 -> display_uncompressed_256c(width, height, bitmap_load_address)
                                        4 -> display_uncompressed_16c(width, height, bitmap_load_address)
                                        2 -> display_uncompressed_4c(width, height, bitmap_load_address)
                                        1 -> display_uncompressed_2c(width, height, bitmap_load_address)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            diskio.f_close()

            if not read_success {
                txt.print("error!\n")
            }
        }

        repeat {
            ; endless loop
        }
    }

    sub set_palette(uword num_colors, ubyte format, uword palletteptr) {
        uword vera_palette_ptr = $fa00
        ubyte red
        ubyte greenblue

        if format {
            ; 3 bytes per color entry, adjust color depth from 8 to 4 bits per channel.
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
        } else {
            ; 2 bytes per color entry, the Vera uses this, but the R/GB bytes order is swapped
            repeat num_colors {
                cx16.vpoke(1, vera_palette_ptr+1, @(palletteptr))
                palletteptr++
                cx16.vpoke(1, vera_palette_ptr, @(palletteptr))
                palletteptr++
                vera_palette_ptr+=2
            }
        }
    }

    sub display_uncompressed_256c(uword width, uword height, uword bitmapptr) {
        uword y
        for y in 0 to height-1 {
            cx16.r0 = 0
            cx16.r1 = y
            cx16.FB_cursor_position()

            ; FB_set_pixels crashes with a size > 255 hence the loop for strips of 128
            ; TODO remove this workaround once the bug is fixed, see https://github.com/commanderx16/x16-rom/issues/179
            cx16.r1 = 128
            ubyte rest = lsb(width) & 127
            repeat width >> 7 {
                cx16.r0 = bitmapptr
                cx16.FB_set_pixels()
                bitmapptr += 128
            }
            if rest {
                cx16.r0 = bitmapptr
                cx16.FB_set_pixels()
                bitmapptr += rest
            }
        }
    }

    sub display_uncompressed_16c(uword width, uword height, uword bitmapptr) {
        ; TODO 16 color
    }

    sub display_uncompressed_4c(uword width, uword height, uword bitmapptr) {
        ; TODO 4 color
    }

    sub display_uncompressed_2c(uword width, uword height, uword bitmapptr) {
        ; TODO 2 color
    }
}
