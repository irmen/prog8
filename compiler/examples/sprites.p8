%import c64lib
%import c64utils
%option force_output, enable_floats


~ spritedata $0a00 {
    %option force_output    ; make sure the data appears in the program

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

        c64.STROUT("balloon sprites!\n")
        c64.STROUT("...we are all floating...\n")

        c64.SPRPTR0 = $0a00//64
        c64.SPRPTR1 = $0a00//64
        c64.SPRPTR2 = $0a00//64
        c64.SPRPTR3 = $0a00//64
        c64.SPRPTR4 = $0a00//64
        c64.SPRPTR5 = $0a00//64
        c64.SPRPTR6 = $0a00//64
        c64.SPRPTR7 = $0a00//64

        for ubyte i in 0 to 7 {
            @(SP0X+i*2) = 50+25*i
            @(SP0Y+i*2) = rnd()
        }

        c64.SPENA = 255                 ; enable all sprites
        c64utils.set_rasterirq(51)     ; enable animation
    }
}


~ irq {
sub irq() {
    c64.EXTCOL--
    ; float up & wobble horizontally

    ; @todo for loop with step 2 doesn't work

    for ubyte i in 0 to 7 {
        @(main.SP0Y+i+i)--
        ubyte r = rnd()
        if r>208
            @(main.SP0X+i+i)++
        else if r<48
            @(main.SP0X+i+i)--
    }
    c64.EXTCOL++
}

}
