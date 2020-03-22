%import c64utils
%import c64lib
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
            c64.SPRPTR[i] = $0a00/64
        }
        c64.SPENA = 255                 ; enable all sprites
        c64utils.set_rasterirq(220)     ; enable animation
    }
}


irq {

    ubyte angle

    sub irq() {
        ubyte @zp spri

        c64.EXTCOL--

        angle++
        c64.MSIGX=0


        for spri in 7 downto 0 {
            uword @zp x = sin8u(angle*2-spri*16) as uword + 50
            ubyte @zp y = cos8u(angle*3-spri*16) / 2 + 70
            c64.SPXYW[spri] = mkword(lsb(x), y)
            lsl(c64.MSIGX)
            if msb(x) c64.MSIGX++
            c64.EXTCOL++
        }
        c64.EXTCOL-=7

    }

}
