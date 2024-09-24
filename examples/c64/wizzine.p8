%import syslib
%import math
%zeropage basicsafe


spritedata $0a00 {
    ; this memory block contains the sprite data
    ; it must start on an address aligned to 64 bytes.
    %option force_output    ; make sure the data in this block appears in the resulting program

    ubyte[] balloonsprite = [ %00000000,%01111111,%00000000,
                              %00000001,%11111111,%11000000,
                              %00000011,%11111111,%11100000,
                              %00000011,%11100011,%11100000,
                              %00000111,%11011100,%11110000,
                              %00000111,%11011101,%11110000,
                              %00000111,%11011100,%11110000,
                              %00000011,%11100011,%11100000,
                              %00000011,%11111111,%11100000,
                              %00000011,%11111111,%11100000,
                              %00000010,%11111111,%10100000,
                              %00000001,%01111111,%01000000,
                              %00000001,%00111110,%01000000,
                              %00000000,%10011100,%10000000,
                              %00000000,%10011100,%10000000,
                              %00000000,%01001001,%00000000,
                              %00000000,%01001001,%00000000,
                              %00000000,%00111110,%00000000,
                              %00000000,%00111110,%00000000,
                              %00000000,%00111110,%00000000,
                              %00000000,%00011100,%00000000   ]
}

main {
    sub start() {
        ubyte i
        for i in 0 to 7 {
            c64.set_sprite_ptr(i, $0a00)           ; alternatively, set directly:  c64.SPRPTR[i] = $0a00 / 64
        }
        c64.SPENA = 255                 ; enable all sprites
        sys.set_rasterirq(&irq.irqhandler, 230)     ; enable animation
    }
}


irq {
    ubyte angle1=200
    ubyte angle2=0

    sub irqhandler() -> bool {
        angle1 += 2
        angle2 += 3
        c64.MSIGX=0
        ubyte @zp spri
        for spri in 7 downto 0 {
            c64.EXTCOL++
            uword @zp x = math.sin8u(angle1-spri*16) as uword + 50
            ubyte @zp y = math.sin8u(angle2-spri*16) / 2 + 70
            c64.SPXYW[spri] = mkword(y, lsb(x))
            if msb(x)!=0
                sys.set_carry()
            rol(c64.MSIGX)
        }
        c64.EXTCOL-=8
        return true
    }
}
