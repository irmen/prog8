%target c64
%import textio
%import syslib
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

        txt.print("balloon sprites!\n...we are all floating...\n")

        ubyte @zp i
        for i in 0 to 7 {
            c64.SPRPTR[i] = $0a00 / 64
            ubyte twoi = i*2        ; TODO is index for array
            c64.SPXY[twoi] = 50+25*i
            twoi++                  ; TODO is index for array
            c64.SPXY[twoi] = rnd()
        }

        c64.SPENA = 255                ; enable all sprites
        c64.set_rasterirq(51)     ; enable animation
    }
}


irq {

    sub irq() {
        c64.EXTCOL--

        ; float up & wobble horizontally
        ubyte @zp i
        for i in 0 to 14 step 2 {
            ubyte ipp=i+1           ; TODO is index for array
            c64.SPXY[ipp]--
            ubyte @zp r = rnd()
            if r>200
                c64.SPXY[i]++
            else if r<40
                c64.SPXY[i]--
        }

        c64.EXTCOL++
    }

}
