%import c64utils


~ spritedata $0a00 {
    ; this memory block contains the sprite data
    ; it must start on an address aligned to 64 bytes.
    %option force_output    ; make sure the data in this block appears in the resulting program

    ubyte[63] balloonsprite = [ %00000000,%01111111,%00000000,
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

~ main {

    sub start() {

        const uword sprite_address_ptr = $0a00 // 64
        c64.SPRPTR0 = sprite_address_ptr
        c64.SPRPTR1 = sprite_address_ptr
        c64.SPRPTR2 = sprite_address_ptr
        c64.SPRPTR3 = sprite_address_ptr
        c64.SPRPTR4 = sprite_address_ptr
        c64.SPRPTR5 = sprite_address_ptr
        c64.SPRPTR6 = sprite_address_ptr
        c64.SPRPTR7 = sprite_address_ptr

        c64.SPENA = 255                ; enable all sprites
        c64utils.set_rasterirq(240)     ; enable animation
    }
}


~ irq {

    ubyte angle=0

sub irq() {
    const uword SP0X = $d000
    const uword SP0Y = $d001

    c64.EXTCOL--

    angle++
    c64.MSIGX=0
    ubyte i=14

nextsprite:     ; @todo should be a for loop from 14 to 0 step -2 but this causes a value out of range error at the moment
    word x = (sin8(angle*2-i*8) as word)+190        ;  @todo will/should be using shifts for faster multiplication and division
    byte y = cos8(angle*3-i*8) // 2                 ;  @todo will/should be using shifts for faster multiplication and division
    @(SP0X+i) = lsb(x)
    @(SP0Y+i) = y+150 as ubyte

    lsl(c64.MSIGX)
    if msb(x) c64.MSIGX++
    i-=2
    if_pl goto nextsprite

    c64.EXTCOL++
}

}
