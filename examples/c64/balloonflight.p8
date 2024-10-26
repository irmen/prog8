%import syslib
%import textio
%import math

; C64 version of a balloon sprites flying over a mountain landscape.
; There is also a X16 version of this in the examples.

main {

    bool do_char_scroll = false

    sub start() {
        uword moon_x = 310
        c64.set_sprite_ptr(0, &spritedata.balloonsprite)       ; alternatively, set directly:  c64.SPRPTR[0] = $0f00 / 64
        c64.set_sprite_ptr(1, &spritedata.moonsprite)          ; alternatively, set directly:  c64.SPRPTR[0] = $0f00 / 64
        c64.SPENA = %00000011
        c64.SP0COL = 14
        c64.SP1COL = 7
        c64.SPXY[0] = 80
        c64.SPXY[1] = 100
        set_moon_pos(moon_x)

        c64.SCROLX &= %11110111     ; 38 column mode
        sys.set_rasterirq(&irq.irqhandler, 250)     ; enable animation via raster interrupt

        ubyte target_height = 10
        ubyte active_height = 25
        bool upwards = true

        repeat {
            ubyte mountain = 223        ; slope upwards
            if active_height < target_height {
                active_height++
                upwards = true
            } else if active_height > target_height {
                mountain = 233          ; slope downwards
                active_height--
                upwards = false
            } else {
                ; determine new height for next mountain
                ubyte old_height = target_height
                if upwards {
                    mountain = 233
                    while target_height >= old_height
                        target_height = 9 + math.rnd() % 15
                } else {
                    mountain = 223
                    while target_height <= old_height
                        target_height = 9 + math.rnd() % 15
                }
            }

            while not do_char_scroll {
                ; let the raster irq do its timing job
            }

            do_char_scroll = false
            scroll_characters_left()

            ; float the balloon and the moon sprites
            if math.rnd() & 1 !=0
                c64.SPXY[1] ++
            else
                c64.SPXY[1] --

            moon_x--
            if msb(moon_x)==255
                moon_x = 340
            set_moon_pos(moon_x)

            ; draw new mountain etc.
            const ubyte RIGHT_COLUMN = 39
            ubyte yy
            for yy in 0 to active_height-1 {
                txt.setcc(RIGHT_COLUMN, yy, 32, 2)         ; clear top of screen
            }
            txt.setcc(RIGHT_COLUMN, active_height, mountain, 8)    ; mountain edge
            for yy in active_height+1 to 24 {
                txt.setcc(RIGHT_COLUMN, yy, 160, 8)        ; draw filled mountain
            }

            ubyte clutter = math.rnd()
            if clutter > 100 {
                ; draw a star
                txt.setcc(RIGHT_COLUMN, clutter % (active_height-1), sc:'.', math.rnd())
            }

            if clutter > 200 {
                ; draw a tree
                ubyte tree = sc:'↑'
                ubyte treecolor = 5
                if clutter & %00010000 != 0
                    tree = sc:'♣'
                else if clutter & %00100000 != 0
                    tree = sc:'♠'
                if math.rnd() > 130
                    treecolor = 13
                txt.setcc(RIGHT_COLUMN, active_height, tree, treecolor)
            }

            if clutter > 235 {
                ; draw a camel
                txt.setcc(RIGHT_COLUMN, active_height, sc:'π', 9)
            }
        }
    }

    sub set_moon_pos(uword x) {
        c64.SPXY[2] = lsb(x)
        c64.SPXY[3] = 55
        if msb(x)!=0
            c64.MSIGX |= %00000010
        else
            c64.MSIGX &= %11111101
    }

    sub scroll_characters_left () {
	    ; Scroll the bottom half (approx.) of the character screen 1 character to the left
	    ; contents of the rightmost column are unchanged, you should clear/refill this yourself
	    ; Without clever split-screen tricks, the C64 is not fast enough to scroll the whole
	    ; screen smootly without tearing. So for simplicity it's constrained to less rows
	    ; such that what is scrolled, *does* scrolls smoothly.
	    ; For maximum performance the scrolling is done in unrolled assembly code.

        %asm {{
		    ldx  #0
		    ldy  #38
-
        .for row=10, row<=24, row+=1
            lda  cbm.Screen + 40*row + 1,x
            sta  cbm.Screen + 40*row + 0,x
            lda  cbm.Colors + 40*row + 1,x
            sta  cbm.Colors + 40*row + 0,x
        .next

		    inx
		    dey
		    bpl  -
    		rts
	    }}
    }
}


irq {
    ; does the smooth scrolling immediately after the visible screen area,
    ; so there is no screen tearing. The main loop does the "big" character
    ; scrolling when the soft-scroll runs out after 8 pixels
    ubyte smoothx=0
    sub irqhandler() -> bool {
        smoothx = (smoothx-1) & 7
        main.do_char_scroll = smoothx==7
        c64.SCROLX = (c64.SCROLX & %11111000) | smoothx
        return false
    }
}


spritedata {
    ; this block contains the sprite data. Sprites must start on an address aligned to 64 bytes.
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

    ubyte[] @align64 moonsprite = [
        %00000000,%00000110,%00000000,
        %00000000,%00011100,%00000000,
        %00000000,%01111000,%00000000,
        %00000000,%11111000,%00000000,
        %00000001,%11110000,%00000000,
        %00000011,%11110000,%00000000,
        %00000011,%11110000,%00000000,
        %00000111,%11100000,%00000000,
        %00000111,%11100000,%00000000,
        %00000111,%11100000,%00000000,
        %00000111,%11100000,%00000000,
        %00000111,%11100000,%00000000,
        %00000111,%11100000,%00000000,
        %00000111,%11100000,%00000000,
        %00000011,%11110000,%00000000,
        %00000011,%11110000,%00000000,
        %00000001,%11110000,%00000000,
        %00000000,%11111000,%00000000,
        %00000000,%01111000,%00000000,
        %00000000,%00011100,%00000000,
        %00000000,%00000110,%00000000
    ]
}
