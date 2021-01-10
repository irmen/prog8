%target cx16
%import gfx2
%import textio
%import diskio

iff_module {
    uword cmap
    uword num_colors
    uword[16] cycle_rates
    uword[16] cycle_rate_ticks
    ubyte[16] cycle_reverseflags
    ubyte[16] cycle_lows
    ubyte[16] cycle_highs
    ubyte num_cycles

    sub show_image(uword filenameptr) -> ubyte {
        ubyte load_ok = false
        uword size
        ubyte[32] buffer
        uword camg = 0
        str chunk_id = "????"
        uword chunk_size_hi
        uword chunk_size_lo
        uword scanline_data_ptr = sys.progend()

        uword width
        uword height
        ubyte num_planes
        ubyte compression
        ubyte have_cmap = false
        ubyte cycle_crng = false
        ubyte cycle_ccrt = false
        num_cycles = 0
        cmap = memory("palette", 256*4)       ; only use 768 of these, but this allows re-use of the same block that the bmp module allocates

        if diskio.f_open(8, filenameptr) {
            size = diskio.f_read(buffer, 12)
            if size==12 {
                if buffer[0]=='f' and buffer[1]=='o' and buffer[2]=='r' and buffer[3]=='m'
                        and buffer[8]=='i' and buffer[9]=='l' and buffer[10]=='b' and buffer[11]=='m'{

                    while read_chunk_header() {
                        if chunk_id == "bmhd" {
                            void diskio.f_read(buffer, chunk_size_lo)
                            width = mkword(buffer[0], buffer[1])
                            height = mkword(buffer[2], buffer[3])
                            num_planes = buffer[8]
                            num_colors = 2 ** num_planes
                            compression = buffer[10]
                        }
                        else if chunk_id == "camg" {
                            void diskio.f_read(buffer, chunk_size_lo)
                            camg = mkword(buffer[2], buffer[3])
                            if camg & $0800 {
                                txt.print("ham mode not supported!\n")
                                break
                            }
                        }
                        else if chunk_id == "cmap" {
                            have_cmap = true
                            void diskio.f_read(cmap, chunk_size_lo)
                        }
                        else if chunk_id == "crng" {
                            ; DeluxePaint color cycle range
                            if not cycle_ccrt {
                                cycle_crng = true
                                void diskio.f_read(buffer, chunk_size_lo)
                                ubyte flags = buffer[5]
                                if flags & 1 {
                                    cycle_rates[num_cycles] = mkword(buffer[2], buffer[3])
                                    cycle_rate_ticks[num_cycles] = 1
                                    cycle_lows[num_cycles] = buffer[6]
                                    cycle_highs[num_cycles] = buffer[7]
                                    cycle_reverseflags[num_cycles] = flags & 2 != 0
                                    num_cycles++
                                }
                            } else
                                skip_chunk()
                        }
                        else if chunk_id == "ccrt" {
                            ; Graphicraft color cycle range
                            if not cycle_crng {
                                cycle_ccrt = true
                                void diskio.f_read(buffer, chunk_size_lo)
                                ubyte direction = buffer[1]
                                if direction {
                                    ; delay_sec = buffer[4] * 256 * 256 * 256 + buffer[5] * 256 * 256 + buffer[6] * 256 + buffer[7]
                                    ; delay_micro = buffer[8] * 256 * 256 * 256 + buffer[9] * 256 * 256 + buffer[10] * 256 + buffer[11]
                                    ; We're ignoring the delay_sec field for now. Not many images will have this slow of a color cycle anyway (>1 sec per cycle)
                                    ; rate = int(16384 // (60*delay_micro/1e6))
                                    ; float rate = (65*16384.0) / (mkword(buffer[9], buffer[10]) as float)  ; fairly good approximation using float arithmetic
                                    cycle_rates[num_cycles] = 33280 / (mkword(buffer[9], buffer[10]) >> 5)      ; reasonable approximation using only 16-bit integer arithmetic
                                    cycle_rate_ticks[num_cycles] = 1
                                    cycle_lows[num_cycles] = buffer[2]
                                    cycle_highs[num_cycles] = buffer[3]
                                    cycle_reverseflags[num_cycles] = direction == 1    ; TODO weird, the spec say that -1 = reversed but several example images that I have downloaded are the opposite
                                    num_cycles++
                                }
                            } else
                                skip_chunk()
                        }
                        else if chunk_id == "body" {
                            gfx2.clear_screen()
                            if camg & $0004
                                height /= 2     ; interlaced: just skip every odd scanline later
                            if camg & $0080 and have_cmap
                                make_ehb_palette()
                            palette.set_rgb8(cmap, num_colors)
                            if compression
                                decode_rle()
                            else
                                decode_raw()
                            load_ok = true
                            break   ; done after body
                        }
                        else {
                            skip_chunk()
                        }
                    }
                } else
                    txt.print("not an iff ilbm file!\n")
            }

            diskio.f_close()
        }

        return load_ok

        sub read_chunk_header() -> ubyte {
            size = diskio.f_read(buffer, 8)
            if size==8 {
                chunk_id[0] = buffer[0]
                chunk_id[1] = buffer[1]
                chunk_id[2] = buffer[2]
                chunk_id[3] = buffer[3]
                chunk_size_hi = mkword(buffer[4], buffer[5])
                chunk_size_lo = mkword(buffer[6], buffer[7])
                return true
            }
            return false
        }

        sub skip_chunk() {
            repeat lsb(chunk_size_hi)*8 + (chunk_size_lo >> 13)
                void diskio.f_read(scanline_data_ptr, $2000)

            void diskio.f_read(scanline_data_ptr, chunk_size_lo & $1fff)
        }

        sub make_ehb_palette() {
            ; generate 32 additional Extra-Halfbrite colors in the cmap
            uword palletteptr = cmap
            uword ehbptr = palletteptr + 32*3
            repeat 32 {
                @(ehbptr) = @(palletteptr)>>1
                ehbptr++
                palletteptr++
                @(ehbptr) = @(palletteptr)>>1
                ehbptr++
                palletteptr++
                @(ehbptr) = @(palletteptr)>>1
                ehbptr++
                palletteptr++
            }
        }

        ubyte bitplane_stride
        uword interleave_stride
        uword offsetx
        uword offsety

        sub start_plot() {
            bitplane_stride = lsb(width>>3)
            interleave_stride = (bitplane_stride as uword) * num_planes
            offsetx = 0
            offsety = 0
            if width < gfx2.width
                offsetx = (gfx2.width - width - 1) / 2
            if height < gfx2.height
                offsety = (gfx2.height - height - 1) / 2
            if width > gfx2.width
                width = gfx2.width
            if height > gfx2.height
                height = gfx2.height
        }

        sub decode_raw() {
            start_plot()
            ubyte interlaced = (camg & $0004) != 0
            uword y
            for y in 0 to height-1 {
                void diskio.f_read(scanline_data_ptr, interleave_stride)
                if interlaced
                    void diskio.f_read(scanline_data_ptr, interleave_stride)
                gfx2.position(offsetx, offsety+y)
                planar_to_chunky_scanline()
            }
        }

        sub decode_rle() {
            start_plot()
            ubyte interlaced = (camg & $0004) != 0
            uword y
            for y in 0 to height-1 {
                decode_rle_scanline()
                if interlaced
                    decode_rle_scanline()
                gfx2.position(offsetx, offsety+y)
                planar_to_chunky_scanline()
            }
        }

        sub decode_rle_scanline() {
            uword x = interleave_stride
            uword plane_ptr = scanline_data_ptr

            while x {
                ubyte b = c64.CHRIN()
                if b > 128 {
                    ubyte b2 = c64.CHRIN()
                    repeat 2+(b^255) {
                        @(plane_ptr) = b2
                        plane_ptr++
                        x--
                    }
                } else if b < 128 {
                    repeat b+1 {
                        @(plane_ptr) = c64.CHRIN()
                        plane_ptr++
                        x--
                    }
                } else
                    break
            }
        }

        sub planar_to_chunky_scanline() {
            ; ubyte[8] masks = [128,64,32,16,8,4,2,1]
            uword x
            for x in 0 to width-1 {
                ; ubyte mask = masks[lsb(x) & 7]
                uword pixptr = x/8 + scanline_data_ptr
                ubyte bits = 0
                %asm {{
                    bra  +
_masks  .byte 128, 64, 32, 16, 8, 4, 2, 1
+                   lda  pixptr
                    sta  P8ZP_SCRATCH_W1
                    lda  pixptr+1
                    sta  P8ZP_SCRATCH_W1+1
                    lda  x
                    and  #7
                    tay
                    lda  _masks,y
                    sta  P8ZP_SCRATCH_B1        ; mask
                    phx
                    ldx  num_planes
                    ldy  #0
-                   lda  (P8ZP_SCRATCH_W1),y
                    clc
                    and  P8ZP_SCRATCH_B1
                    beq  +
                    sec
+                   ror  bits                   ; shift planar bit into chunky byte
                    lda  P8ZP_SCRATCH_W1
                    ; clc
                    adc  bitplane_stride
                    sta  P8ZP_SCRATCH_W1
                    bcc  +
                    inc  P8ZP_SCRATCH_W1+1
+                   dex
                    bne  -
                    plx
                    lda  #8
                    sec
                    sbc  num_planes
                    beq  +
                    tay
-                   lsr  bits
                    dey
                    bne  -
+
                }}

; the assembly above is the optimized version of this:
;                repeat num_planes {
;                    clear_carry()
;                    if @(pixptr) & mask
;                        set_carry()
;                    ror(bits)           ; shift planar bit into chunky byte
;                    pixptr += bitplane_stride
;                }
;                bits >>= 8-num_planes

                gfx2.next_pixel(bits)
            }
        }
    }

    sub cycle_colors_each_jiffy() {
        if num_cycles==0
            return

        ; TODO implement Blend Shifting see http://www.effectgames.com/demos/canvascycle/palette.js

        ubyte changed = false
        ubyte ci
        for ci in 0 to num_cycles-1 {
            cycle_rate_ticks[ci]--
            if cycle_rate_ticks[ci]==0 {
                changed = true
                cycle_rate_ticks[ci] = 16384 / cycle_rates[ci]
                do_cycle(cycle_lows[ci], cycle_highs[ci], cycle_reverseflags[ci])
            }
        }

        if changed
            palette.set_rgb8(cmap, num_colors)     ; set the new palette

        sub do_cycle(uword low, uword high, ubyte reversed) {
            low *= 3
            high *= 3
            uword bytecount = high-low
            uword cptr
            ubyte red
            ubyte green
            ubyte blue

            if reversed {
                cptr = cmap + low
                red = @(cptr)
                green = @(cptr+1)
                blue = @(cptr+2)
                repeat bytecount {
                    @(cptr) = @(cptr+3)
                    cptr++
                }
                @(cptr) = red
                cptr++
                @(cptr) = green
                cptr++
                @(cptr) = blue
            } else {
                cptr = cmap + high
                red = @(cptr)
                cptr++
                green = @(cptr)
                cptr++
                blue = @(cptr)
                repeat bytecount {
                    @(cptr) = @(cptr-3)
                    cptr--
                }
                @(cptr) = blue
                cptr--
                @(cptr) = green
                cptr--
                @(cptr) = red
            }
        }
    }
}
