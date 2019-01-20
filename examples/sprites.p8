%import c64utils
%import c64lib


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

    const uword SP0X = $d000
    const uword SP0Y = $d001

    sub start() {

        c64scr.print("balloon sprites!\n...we are all floating...\n")

        for ubyte i in 0 to 7 {
            c64.SPRPTR[i] = $0a00 / 64
            c64.SPXY[i*2] = 50+25*i
            c64.SPXY[i*2+1] = rnd()
        }

        c64.SPENA = 255                ; enable all sprites
        c64utils.set_rasterirq(51)     ; enable animation
    }
}


~ irq {
sub irq() {
    c64.EXTCOL--

    ; float up & wobble horizontally
    for ubyte i in 0 to 14 step 2 {
        c64.SPXY[i+1]--
        ubyte r = rnd()
        if r>200
            c64.SPXY[i]++
        else if r<40
            c64.SPXY[i]--
    }

    c64.EXTCOL++
}

}
