; %import graphics
%zeropage basicsafe

main {
    sub start2() {

        word xx
        word yy

        ; all comparisons with constant values are already optimized.
        ; but for variables (and memreads) we can optimize further (don't use temporary ZP location)

        word value = 100

        while xx==value {
            yy++
        }

        while xx!=value {
            yy++
        }

        do {
            yy++
        } until xx==value

        do {
            yy++
        } until xx!=value

        if xx==value
            yy++

        if xx!=value
            yy++

;        while xx>value {
;            yy++
;        }
;        do {
;            yy++
;        } until xx>value
;
;        if xx>value
;            yy++
    }

    sub start() {
        uword uw1
        uword uw2

        if uw1<uw2+2 {
            uw1++
        }

        byte xx
        byte yy

        ; all comparisons with constant values are already optimized.
        ; but for variables (and memreads) we can optimize further (don't use temporary ZP location)

        byte value = 100

        while xx==value {
            yy++
        }

        while xx==@($2000) {
            yy++
        }

        while xx!=value {
            yy++
        }

        while xx!=@($2000) {
            yy++
        }

        do {
            yy++
        } until xx==value

        do {
            yy++
        } until xx!=value

        if xx==value
            yy++

        if xx!=value
            yy++

        while xx>value {
            yy++
        }
        while xx<value {
            yy++
        }
        do {
            yy++
        } until xx>value
        do {
            yy++
        } until xx<value

        if xx>value
            yy++
        if xx<value
            yy++
    }


;    sub start2() {
;
;        graphics.enable_bitmap_mode()
;
;        uword xx
;        ubyte yy
;
;        graphics.line(150,50,150,50)
;
;        for yy in 0 to 199-60 step 16 {
;
;            for xx in 0 to 319-50 step 16 {
;                graphics.line(30+xx, 10+yy, 50+xx, 30+yy)
;                graphics.line(49+xx, 30+yy, 10+xx, 30+yy)
;                graphics.line(11+xx, 29+yy, 29+xx, 11+yy)
;
;                ; triangle 2, counter-clockwise
;                graphics.line(30+xx, 40+yy, 10+xx, 60+yy)
;                graphics.line(11+xx, 60+yy, 50+xx, 60+yy)
;                graphics.line(49+xx, 59+yy, 31+xx,41+yy)
;            }
;        }
;
;        repeat {
;        }
;    }
}
