%import c64lib
%import c64utils

~ main {

    const uword SP0X = $d000
    const uword SP0Y = $d001

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

    const uword sprite_data_address = 13*64       ; // safe area inside the tape buffer

    sub start() {

        c64.STROUT("balloon sprites!\n")
        c64.STROUT("...we are all floating...\n")

        ; copy the ballon sprite data to the correct address and setup the sprite pointers
        ; @todo make a memcopy function for this, that calls c64utils.memcopy
        for ubyte i in 0 to 63 {
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

        for ubyte i in 0 to 7 {
            @(SP0X+i*2) = 50+25*i
            @(SP0Y+i*2) = rnd()
        }

        c64.SPENA = 255     ; enable all sprites
    }
}


~ irq {
; @todo no longer auto-set this as irq handler. instead, add builtins functions activate_irqvec() / restore_irqvec()
sub irq() {
    ;return      ; @todo return statements in the irqhandler should not do rts, but instead jmp  c64.IRQDFRT
    ; @todo also when including this return, the jmp  c64.IRQDFRT at the end gets omitted.....:(
    c64.EXTCOL++
    for ubyte i in 0 to 7 {
        @(main.SP0Y+i+i)--          ; float up

        ; horizontal wobble effect
        ubyte r = rnd()
        if r>208
            @(main.SP0X+i+i)++
        else {
            ; @todo if-else if -else syntax without curly braces
            if r<48
                @(main.SP0X+i+i)--
        }
    }
    c64.EXTCOL--
}

}
