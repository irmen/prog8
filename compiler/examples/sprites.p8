%import c64lib
%import c64utils

~ main {

    ubyte[63] balloonsprite = [ %00000000, %01111111, %00000000,
                                %00000001, %11111111, %11000000,
                                %00000011, %11111111, %11100000,
                                %00000011, %11100011, %11100000,
                                %00000111, %11011100, %11110000,
                                %00000111, %11011101, %11110000,
                                %00000111, %11011100, %11110000,
                                %00000011, %11100011, %11100000,
                                %00000011, %11111111, %11100000,
                                %00000011, %11111111, %11100000,
                                %00000010, %11111111, %10100000,
                                %00000001, %01111111, %01000000,
                                %00000001, %00111110, %01000000,
                                %00000000, %10011100, %10000000,
                                %00000000, %10011100, %10000000,
                                %00000000, %01001001, %00000000,
                                %00000000, %01001001, %00000000,
                                %00000000, %00111110, %00000000,
                                %00000000, %00111110, %00000000,
                                %00000000, %00111110, %00000000,
                                %00000000, %00011100, %00000000   ]

    const uword sprite_data_address = 13*64       ; // safe area inside the tape buffer

    sub start() {

        c64.STROUT("balloon sprites!\n")
        c64.STROUT("...we are all floating...\n")

        ; copy the ballon sprite data to the correct address and setup the sprite pointers
        ; @todo make a memcopy function for this, that calls c64utils.memcopy
        for ubyte i in 0 to 62 {
            ;@(sprite_data_address+i) = @(balloonsprite+i)           ; @todo nice error message
            @(sprite_data_address+i) = balloonsprite[i]
        }

        c64.SPRPTR0 = sprite_data_address//64
        c64.SPRPTR1 = sprite_data_address//64
        c64.SPRPTR2 = sprite_data_address//64
        c64.SPRPTR3 = sprite_data_address//64
        c64.SPRPTR4 = sprite_data_address//64
        c64.SPRPTR5 = sprite_data_address//64
        c64.SPRPTR6 = sprite_data_address//64
        c64.SPRPTR7 = sprite_data_address//64

        c64.SP0X = 30+30*0
        c64.SP1X = 30+30*1
        c64.SP2X = 30+30*2
        c64.SP3X = 30+30*3
        c64.SP4X = 30+30*4
        c64.SP5X = 30+30*5
        c64.SP6X = 30+30*6
        c64.SP7X = 30+30*7

        c64.SP0Y = 100+10*0
        c64.SP1Y = 100+10*1
        c64.SP2Y = 100+10*2
        c64.SP3Y = 100+10*3
        c64.SP4Y = 100+10*4
        c64.SP5Y = 100+10*5
        c64.SP6Y = 100+10*6
        c64.SP7Y = 100+10*7

        c64.SPENA = 255     ; enable all sprites
    }
}


~ irq {

sub irq() {
    c64.SP0Y--
    c64.SP1Y--
    c64.SP2Y--
    c64.SP3Y--
    c64.SP4Y--
    c64.SP5Y--
    c64.SP6Y--
    c64.SP7Y--


;    const uword SP0X = $d000
;    for ubyte i in 0 to 7 {
;        @(SP0X+i*2) = @(SP0X+i*2) + 1   ; @todo this doesn't read (or write?) the correct values..
;        ubyte r = rnd()
;        if r>228
;            ; @(SP0X+i*2)++       ; @todo allow this
;            @(SP0X+i*2) = @(SP0X+i*2)+1
;        else {
;            if r<28
;                @(SP0X+i*2) = @(SP0X+i*2)-1
;        }
;    }
}

}
