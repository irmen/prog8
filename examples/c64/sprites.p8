%import textio
%import syslib
%import math
%zeropage basicsafe

main {

    sub start() {

        txt.print("balloon sprites!\n...we are all floating...\nborders are open too\n")

        ubyte @zp i
        for i in 0 to 7 {
            c64.set_sprite_ptr(i, &spritedata.balloonsprite)           ; alternatively, set directly:  c64.SPRPTR[i] = $0a00 / 64
            c64.SPXY[i*2] = 60+22*i
            c64.SPXY[i*2+1] = math.rnd()
        }

        c64.SPENA = 255       ; enable all sprites
        sys.set_rasterirq(&irq.irqhandler, 248)         ; trigger irq just above bottom border line
    }
}


irq {

    sub irqhandler() -> bool {
        c64.SCROLY = 19             ; 24 row mode, preparing for border opening
        c64.EXTCOL--

        ; float up & wobble horizontally
        ubyte @zp i
        for i in 0 to 14 step 2 {
            c64.SPXY[i+1]--
            ubyte @zp r = math.rnd()
            if r>200
                c64.SPXY[i]++
            else if r<40
                c64.SPXY[i]--
        }

        c64.EXTCOL++
        c64.SCROLY = 27            ; 25 row mode, border is open
        return true
    }

}

spritedata {
    ; this memory block contains the sprite data
    ; it must start on an address aligned to 64 bytes.

    ubyte[] @align64 balloonsprite = [
        %00000000,%01111111,%00000000,
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
        %00000000,%00011100,%00000000
    ]
}
