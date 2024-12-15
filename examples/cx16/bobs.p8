%import palette
%import conv
%import textio
%import math

; "unlimited sprites / bobs" demo effect.
; Note that everything is prog8, no inline assembly used/required.
; Note2: these aren't actual sprites, it's a bitmap backbuffer display trick.
; TODO don't add bobs in pairs of 2 make them appear individually

main {
    sub start() {
        void cx16.screen_mode(3, false)
        txt.print("\n\n how many sprites does\n    the commander x16 have?\n")
        sys.wait(120)
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

        cx16.enable_irq_handlers(true)
        cx16.set_line_irq_handler(340, &irq)  ; time it so that the page flip at the end occurs near the bottom of the screen to avoid tearing

        repeat {
            ; don't exit
        }
    }

    const ubyte num_backbuffers = 12        ; there is vram space for 14 backbuffers.  reduce to make tighter "loops"
    uword num_bobs = 0
    ubyte backbuffer = num_backbuffers-1
    ubyte blitbuffer = 0
    uword anim1 = $0432
    uword anim2 = $f123
    uword anim3 = $e321
    uword anim4 = $7500

    sub irq() -> bool {

        ; palette.set_color(0, $f00)          ; debug rastertime

        ; draw 4 bobs per frame to speed up bob count
        repeat 4 {
            ubyte vmembase = blitbuffer*4               ; 2048 * 4 per backbuffer
            blit(vmembase)
            blitbuffer++
            if blitbuffer==num_backbuffers
                blitbuffer=0
        }

        backbuffer++
        if backbuffer==num_backbuffers {
            backbuffer=0
            num_bobs+=4
        }

        vmembase = backbuffer*4                     ; 2048 * 4 per backbuffer
        draw_number(vmembase, num_bobs)
        cx16.VERA_L1_TILEBASE = vmembase << 2       ; flip to next backbuffer

        ; palette.set_color(0, $000)
        return false
    }

    sub init_buffers() {
        ; erase all vram
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = 0
        cx16.VERA_ADDR_M = 0
        cx16.VERA_ADDR_H = %00010000        ; auto incr 1
        repeat $ffff
            cx16.VERA_DATA0 = 0
        repeat $f960
            cx16.VERA_DATA0 = 0
    }

    sub blit(ubyte vmembase) {
        ubyte bank = vmembase>=32 as ubyte
        uword vmem = vmembase * 2048        ; mkword(vmembase,0) * 8
        uword blit_x = (math.cos8u(msb(anim1)) as uword) + math.sin8u(msb(anim2))/6
        ubyte blit_y = math.sin8u(msb(anim3))/2  + math.cos8u(msb(anim4))/5
        vmem += blit_x/8 + (blit_y as uword) * 40

        bitshift(lsb(blit_x) & 7)

        ; left column of the (shifted) "sprite" (bob)
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = lsb(vmem)
        cx16.VERA_ADDR_M = msb(vmem)
        cx16.VERA_ADDR_H = bank | %10110000     ; increment 40 for read (next line)
        cx16.VERA_CTRL = 1
        cx16.VERA_ADDR_L = lsb(vmem)
        cx16.VERA_ADDR_M = msb(vmem)
        cx16.VERA_ADDR_H = bank | %10110000     ; increment 40 for read (next line)
        ubyte ix
        for ix in 0 to len(shifted_sprite)-1 step 3
            cx16.VERA_DATA1 = cx16.VERA_DATA0 & shifted_mask[ix] | shifted_sprite[ix]

        ; middle column of the (shifted) "sprite" (bob)
        vmem++
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = lsb(vmem)
        cx16.VERA_ADDR_M = msb(vmem)
        cx16.VERA_CTRL = 1
        cx16.VERA_ADDR_L = lsb(vmem)
        cx16.VERA_ADDR_M = msb(vmem)
        for ix in 1 to len(shifted_sprite)-1 step 3
            cx16.VERA_DATA1 = cx16.VERA_DATA0 & shifted_mask[ix] | shifted_sprite[ix]

        ; right column of the (shifted) "sprite" (bob)
        vmem++
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = lsb(vmem)
        cx16.VERA_ADDR_M = msb(vmem)
        cx16.VERA_CTRL = 1
        cx16.VERA_ADDR_L = lsb(vmem)
        cx16.VERA_ADDR_M = msb(vmem)
        for ix in 2 to len(shifted_sprite)-1 step 3
            cx16.VERA_DATA1 = cx16.VERA_DATA0 & shifted_mask[ix] | shifted_sprite[ix]

        anim1 += 117
        anim2 += 90
        anim3 += 122
        anim4 += 145
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
        ubyte bank = vmembase>=32 as ubyte
        vmem += 35
        uword number_str = conv.str_uw0(number)
        uword pixelsptr = &numberpixels + (number_str[1] & 15)*7
        ubyte pix
        cx16.VERA_CTRL = 0
        cx16.VERA_ADDR_L = lsb(vmem)
        cx16.VERA_ADDR_M = msb(vmem)
        cx16.VERA_ADDR_H = bank | %10110000       ; increment 40 for read (next line)
        for pix in 0 to 6
            cx16.VERA_DATA0 = pixelsptr[pix]
        vmem++
        cx16.VERA_ADDR_L = lsb(vmem)
        cx16.VERA_ADDR_M = msb(vmem)
        pixelsptr = &numberpixels + (number_str[2] & 15)*7
        for pix in 0 to 6
            cx16.VERA_DATA0 = pixelsptr[pix]
        vmem++
        cx16.VERA_ADDR_L = lsb(vmem)
        cx16.VERA_ADDR_M = msb(vmem)
        pixelsptr = &numberpixels + (number_str[3] & 15)*7
        for pix in 0 to 6
            cx16.VERA_DATA0 = pixelsptr[pix]
        vmem++
        cx16.VERA_ADDR_L = lsb(vmem)
        cx16.VERA_ADDR_M = msb(vmem)
        pixelsptr = &numberpixels + (number_str[4] & 15)*7
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
        %0000001111000000,
        %0001111111111000,
        %0011111110101100,
        %0111111011010010,
        %0111111010100010,
        %0111110100001010,
        %1111101000100001,
        %1111010000000001,
        %1111000100100001,
        %1110100000000001,
        %0110001000000010,
        %0101000000000010,
        %0100000000000010,
        %0010100000000100,
        %0001110000111000,
        %0000001111000000
    ]

    uword[16] mask = [
        %1111110000111111,
        %1110000000000111,
        %1100000000000011,
        %1000000000000001,
        %1000000000000001,
        %1000000000000001,
        %0000000000000000,
        %0000000000000000,
        %0000000000000000,
        %0000000000000000,
        %1000000000000001,
        %1000000000000001,
        %1000000000000001,
        %1100000000000011,
        %1110000000000111,
        %1111110000111111
    ]

    ubyte[16*3] shifted_sprite
    ubyte[16*3] shifted_mask
}
