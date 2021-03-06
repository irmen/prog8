%target cx16
%import palette
%import conv
%import textio

; "unlimited sprites / bobs" demo effect.
; Note that everything is prog8, no inline assembly used/required.


main {
    sub start() {
        cx16.screen_set_mode(0)
        txt.print("\n\n how many sprites does\n    the commander x16 have?\n")
        sys.wait(180)
        txt.print("\n\n the manual says: '128'.\n")
        sys.wait(80)
        txt.print("\n\n but that's just a manual...\n")
        sys.wait(80)
        txt.print("\n\n let's find out for ourselves,\n        shall we?")
        sys.wait(180)

        ; enable bitmap mode 320x240, 1 bpp, only layer 1
        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00100000
        cx16.VERA_DC_HSCALE = 64
        cx16.VERA_DC_VSCALE = 64
        cx16.VERA_L1_CONFIG = %00000100
        cx16.VERA_L1_MAPBASE = 0
        cx16.VERA_L1_TILEBASE = 0

        ; limit display heigth to 200 pixels to have enough vram for 14 backbuffers
        const ubyte vstart = 20
        const ubyte vheight = 200
        cx16.VERA_CTRL = %00000010
        cx16.VERA_DC_VSTART = vstart
        cx16.VERA_DC_VSTOP = vstart + vheight - 1

        init_buffers()
        palette.set_color(0, $000)
        palette.set_color(1, $af8)

        cx16.set_irq(&irq, false)

        repeat {
            ; don't exit
        }
    }

    const ubyte num_backbuffers = 12        ; there is vram space for 14 backbuffers.  reduce to make tighter "loops"
    uword num_bobs = 0
    ubyte backbuffer = num_backbuffers-1
    ubyte blitbuffer = 0
    uword anim1 = $0432
    uword anim2 = $0123
    uword anim3 = $4321
    uword anim4 = $8500

    sub irq() {

        ; palette.set_color(0, $f00)          ; debug rastertime

        ; draw 2 bobs per frame to speed up bob count
        ubyte vmembase = blitbuffer*4               ; 2048 * 4 per backbuffer
        blit(vmembase)
        blitbuffer++
        if blitbuffer==num_backbuffers
            blitbuffer=0
        vmembase = blitbuffer*4                     ; 2048 * 4 per backbuffer
        blit(vmembase)
        blitbuffer++
        if blitbuffer==num_backbuffers
            blitbuffer=0

        backbuffer++
        if backbuffer==num_backbuffers {
            backbuffer=0
            num_bobs+=2
        }

        vmembase = backbuffer*4                     ; 2048 * 4 per backbuffer
        draw_number(vmembase, num_bobs)
        cx16.VERA_L1_TILEBASE = vmembase << 2       ; flip to next backbuffer

        ; palette.set_color(0, $000)
    }

    sub init_buffers() {
        ; erase all vram
        cx16.vaddr(0, 0, 0, true)
        repeat $ffff
            cx16.VERA_DATA0 = 0
        repeat $f960
            cx16.VERA_DATA0 = 0
    }

    sub blit(ubyte vmembase) {
        ubyte bank = vmembase>=32
        uword vmem = vmembase * 2048        ; mkword(vmembase,0) * 8
        uword blit_x = (cos8u(msb(anim1)) as uword) + sin8u(msb(anim2))/5
        ubyte blit_y = sin8u(msb(anim3))/2  + cos8u(msb(anim4))/5
        vmem += blit_x/8 + (blit_y as uword) * 40

        bitshift(lsb(blit_x) & 7)

        ; left column of the (shifted)sprite
        ; TODO don't call vaddr, inline it here
        cx16.vaddr(bank, vmem, 0, false)
        cx16.VERA_ADDR_H &= 1
        cx16.VERA_ADDR_H |= %10110000   ; increment 40 for read (next line)
        cx16.vaddr(bank, vmem, 1, false)
        cx16.VERA_ADDR_H &= 1
        cx16.VERA_ADDR_H |= %10110000   ; increment 40 for write (next line)
        ubyte ix
        for ix in 0 to len(shifted_sprite)-1 step 3
            cx16.VERA_DATA1 = cx16.VERA_DATA0 & shifted_mask[ix] | shifted_sprite[ix]

        ; middle column of the (shifted)sprite
        cx16.vaddr(bank, vmem+1, 0, false)
        cx16.VERA_ADDR_H &= 1
        cx16.VERA_ADDR_H |= %10110000   ; increment 40 for read (next line)
        cx16.vaddr(bank, vmem+1, 1, false)
        cx16.VERA_ADDR_H &= 1
        cx16.VERA_ADDR_H |= %10110000   ; increment 40 for write (next line)
        for ix in 1 to len(shifted_sprite)-1 step 3
            cx16.VERA_DATA1 = cx16.VERA_DATA0 & shifted_mask[ix] | shifted_sprite[ix]

        ; right column of the (shifted)sprite
        cx16.vaddr(bank, vmem+2, 0, false)
        cx16.VERA_ADDR_H &= 1
        cx16.VERA_ADDR_H |= %10110000   ; increment 40 for read (next line)
        cx16.vaddr(bank, vmem+2, 1, false)
        cx16.VERA_ADDR_H &= 1
        cx16.VERA_ADDR_H |= %10110000   ; increment 40 for write (next line)
        for ix in 2 to len(shifted_sprite)-1 step 3
            cx16.VERA_DATA1 = cx16.VERA_DATA0 & shifted_mask[ix] | shifted_sprite[ix]

        anim1 += 217
        anim2 += 190
        anim3 += 222
        anim4 += 195
;        anim1 += 107
;        anim2 += 80
;        anim3 += 122
;        anim4 += 93
    }

    sub bitshift(ubyte shift) {
        ubyte yix
        ubyte yy
        for yy in 0 to 15 {
            uword @zp sprw = sprite[yy]
            uword @zp maskw = mask[yy]
            ubyte @zp sprite_3 = 0
            ubyte @zp mask_3 = 255
            repeat shift {
                sprw >>= 1
                ror(sprite_3)
                sys.set_carry()
                ror(maskw)
                ror(mask_3)
            }
            shifted_sprite[yix] = msb(sprw)
            shifted_mask[yix] = msb(maskw)
            yix++
            shifted_sprite[yix] = lsb(sprw)
            shifted_mask[yix] = lsb(maskw)
            yix++
            shifted_sprite[yix] = sprite_3
            shifted_mask[yix] = mask_3
            yix++
        }
    }

    sub draw_number(ubyte vmembase, uword number) {
        uword vmem = vmembase * 2048        ; mkword(vmembase,0) * 8
        ubyte bank = vmembase>=32
        vmem += 35
        conv.str_uw0(number)
        uword pixelsptr = &numberpixels + (conv.string_out[1] & 15)*7
        ubyte pix
        cx16.vaddr(bank, vmem, 0, false)
        cx16.VERA_ADDR_H &= 1
        cx16.VERA_ADDR_H |= %10110000   ; increment 40 for read (next line)
        for pix in 0 to 6
            cx16.VERA_DATA0 = pixelsptr[pix]
        vmem++
        cx16.vaddr(bank, vmem, 0, false)
        cx16.VERA_ADDR_H &= 1
        cx16.VERA_ADDR_H |= %10110000   ; increment 40 for read (next line)
        pixelsptr = &numberpixels + (conv.string_out[2] & 15)*7
        for pix in 0 to 6
            cx16.VERA_DATA0 = pixelsptr[pix]
        vmem++
        cx16.vaddr(bank, vmem, 0, false)
        cx16.VERA_ADDR_H &= 1
        cx16.VERA_ADDR_H |= %10110000   ; increment 40 for read (next line)
        pixelsptr = &numberpixels + (conv.string_out[3] & 15)*7
        for pix in 0 to 6
            cx16.VERA_DATA0 = pixelsptr[pix]
        vmem++
        cx16.vaddr(bank, vmem, 0, false)
        cx16.VERA_ADDR_H &= 1
        cx16.VERA_ADDR_H |= %10110000   ; increment 40 for read (next line)
        pixelsptr = &numberpixels + (conv.string_out[4] & 15)*7
        for pix in 0 to 6
            cx16.VERA_DATA0 = pixelsptr[pix]
    }

    ubyte[10*7] numberpixels = [
        %00111000,
        %01000100,
        %10000100,
        %10000100,
        %10000100,
        %01111000,
        %00000000,

        %00010000,
        %00110000,
        %01010000,
        %00010000,
        %00010000,
        %01111100,
        %00000000,

        %01111000,
        %10000100,
        %00011000,
        %00110000,
        %01100000,
        %11111100,
        %00000000,

        %01111000,
        %00000100,
        %00111000,
        %00000100,
        %00000100,
        %11111000,
        %00000000,

        %00010100,
        %00100100,
        %01000100,
        %11111100,
        %00000100,
        %00000100,
        %00000000,

        %11111000,
        %10000000,
        %11111000,
        %00000100,
        %00000100,
        %11111000,
        %00000000,

        %01111000,
        %10000000,
        %11111000,
        %10000100,
        %10000100,
        %01111000,
        %00000000,

        %11111100,
        %00001000,
        %00010000,
        %00010000,
        %00010000,
        %00010000,
        %00000000,

        %01111000,
        %10000100,
        %01111000,
        %10000100,
        %10000100,
        %01111000,
        %00000000,

        %01111000,
        %10000100,
        %01111100,
        %00000100,
        %10000100,
        %01111000,
        %00000000
    ]

    uword[16] sprite = [
        %0000000000000000,
        %0110001110000000,
        %0101001001000000,
        %0100111001000000,
        %0100000000100000,
        %0101001000100000,
        %0101001000110000,
        %0100100000101000,
        %0101111000101000,
        %0010000001010100,
        %0001111110010100,
        %0001000000010000,
        %0001000000010000,
        %0001010111010000,
        %0001101100110000,
        %0000000000000000
    ]

    uword[16] mask = [
        %1111111111111111,
        %1000000001111111,
        %1000000000111111,
        %1000000000111111,
        %1000000000011111,
        %1000000000011111,
        %1000000000001111,
        %1000000000000111,
        %1000000000000111,
        %1100000000001011,
        %1110000000001011,
        %1110000000001111,
        %1110000000001111,
        %1110000000001111,
        %1110010011001111,
        %1111111111111111
    ]

    ubyte[16*3] shifted_sprite
    ubyte[16*3] shifted_mask
}
