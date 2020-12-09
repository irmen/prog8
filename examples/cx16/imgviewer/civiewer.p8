%target cx16
%import graphics
%import textio
%import diskio
%import c64colors
%zeropage basicsafe

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
;                                      01 = PCX-RLE    [TODO not yet implemented]
;                                      10 = LZSA       [TODO not yet implemented]
;                                      11 = Exomizer   [TODO not yet implemented]
;          bit 2 = palette format.  0 = 4 bits/channel  (2 bytes per color, $0R $GB)  [TODO not yet implemented]
;                                   1 = 8 bits/channel  (3 bytes per color, $RR $GG $BB)
;                 4 bits per channel is the Cx16's native palette format.
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

main {

    ubyte[256] buffer
    ubyte[256] buffer2
    ubyte[256] buffer3

    str filename = "trsi256.ci"

    sub start() {
        buffer[0] = 0
        buffer2[0] = 0
        buffer3[0] = 0

        ubyte read_success = false

        if(io.f_open(8, filename)) {
            uword size = io.f_read(buffer, 12)  ; read the header
            if size==12 {
                if buffer[0]=='c' and buffer[1]=='i' and buffer[2] == 9 {
                    if buffer[11] {
                        txt.print("file too large >64kb!\n")       ; TODO add support for large files
                    } else {
                        uword width = mkword(buffer[4], buffer[3])
                        uword height = mkword(buffer[6], buffer[5])
                        ubyte bpp = buffer[7]
                        uword num_colors = 1
                        num_colors <<= bpp  ; TODO FIX THIS:  uword num_colors = 1 << bpp
                        ubyte flags = buffer[8]
                        ubyte compression = flags & %00000011
                        ubyte palette_format = (flags & %00000100) >> 2
                        ubyte bitmap_format = (flags & %00001000) >> 3
                        ubyte hscale = (flags & %00010000) >> 4
                        ubyte vscale = (flags & %00100000) >> 5
                        uword bitmap_size = mkword(buffer[10], buffer[9])
                        uword palette_size

                        if palette_format
                            palette_size = 3*num_colors
                        else
                            palette_size = 2*num_colors
                        txt.print(filename)
                        txt.print("\ndimensions: ")
                        txt.print_uw(width)
                        txt.chrout('*')
                        txt.print_uw(height)
                        txt.print("\nbits-per-pixel: ")
                        txt.print_ub(bpp)
                        txt.print(" #colors: ")
                        txt.print_uw(num_colors)
                        txt.print("\ncompression: ")
                        txt.print_ub(compression)
                        txt.print("\npalette format: ")
                        txt.print_ub(palette_format)
                        txt.print("\nbitmap format: ")
                        txt.print_ub(bitmap_format)
                        txt.print("\nscaling: ")
                        txt.print_ub(hscale)
                        txt.chrout(',')
                        txt.print_ub(vscale)
                        txt.chrout('\n')
                        if compression!=0 {
                            txt.print("compression not yet supported\n")    ; TODO implement this
                        } else if bitmap_format==1 {
                            txt.print("tiled bitmap not yet supported\n")       ; TODO implement this
                        } else {
                            txt.print("reading palette and bitmap...\n")
                            size = io.f_read(buffer, palette_size)
                            if size==palette_size {
                                size = io.f_read($3000, bitmap_size)
                                read_success = size==bitmap_size
                            }
                        }
                    }
                }
            } else {
                txt.print("io error or invalid image file\n")
            }

            io.f_close()

            if read_success {
                txt.print("done!\n")
            } else {
                txt.print("error loading image file.\n")
            }
        }

        txt.print(diskio.status(8))
    }
}

io {

    ubyte iteration_in_progress = false

    sub f_open(ubyte drivenumber, uword filenameptr) -> ubyte {
        f_close()

        c64.SETNAM(strlen(filenameptr), filenameptr)
        c64.SETLFS(11, drivenumber, 0)
        void c64.OPEN()          ; open 11,8,0,"filename"
        if_cc {
            iteration_in_progress = true
            void c64.CHKIN(11)        ; use #2 as input channel
            if_cc
                return true
        }
        f_close()
        return false
    }

    sub f_read(uword bufferpointer, uword buffersize) -> uword {
        if not iteration_in_progress
            return 0

        uword actual = 0
        repeat buffersize {
            ubyte data = c64.CHRIN()
            @(bufferpointer) = data
            bufferpointer++
            actual++
            ubyte status = c64.READST()
            if status==64
                f_close()       ; end of file, close it
            if status
                return actual
        }
        return actual
    }

    sub f_close() {
        ; -- end an iterative file loading session (close channels).
        if iteration_in_progress {
            c64.CLRCHN()
            c64.CLOSE(11)
            iteration_in_progress = false
        }
    }
}
