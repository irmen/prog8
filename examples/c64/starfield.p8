; Galencia starfield ported to Prog8. Original: https://github.com/JasonAldred/C64-Starfield
; This is for the C64 only.

%option no_sysinit

main {
    const uword starScreenChar  = $0400         ; Screen address
    const uword StarScreenCols  = $d800         ; Character attribute address

    const uword charBase  = $3000         ; Address of our character set
    const uword star1Init = charBase+$1d0 ; Init address for each star
    const uword star2Init = charBase+$298
    const uword star3Init = charBase+$240
    const uword star4Init = charBase+$2e0

    const uword star1Limit  = charBase+$298 ; Limit for each star
    const uword star2Limit  = charBase+$360 ; Once limit is reached, they are reset
    const uword star3Limit  = charBase+$298
    const uword star4Limit  = charBase+$360
    const uword star1Reset  = charBase+$1d0 ; Reset address for each star
    const uword star2Reset  = charBase+$298
    const uword star3Reset  = charBase+$1d0
    const uword star4Reset  = charBase+$298
    const uword staticStar1 = charBase+$250 ; 2 Locations for blinking static stars
    const uword staticStar2 = charBase+$1e0

    const ubyte starColourLimit = 20            ; use values 1 to 20
                                ; Galencia uses these values
                                ; 1     = mono
                                ; 2     = duo
                                ; 20    = full colour

    ; 4 x pointers for moving stars
    uword @zp starfieldPtr1
    uword @zp starfieldPtr2
    uword @zp starfieldPtr3
    uword @zp starfieldPtr4

    ubyte rasterCount    ; Counter that increments each frame

    sub start () {
        sys.set_irqd()
        sys.memset(charBase, 8*256, 0)     ; clear charset data
        c64.EXTCOL = 0
        c64.BGCOL0 = 0
        c64.VMCSB = (charBase/1024) | %00010000     ; Characters at $3000
        initStarfield()
        createStarScreen()

        repeat {
            sys.waitvsync()
            rasterCount ++
            doStarfield()
        }
    }

    sub doStarfield() {
        ; This routine does 3 things:
        ; 1) Erases stars
        ; 2) Moves stars
        ; 3) Draws stars in new position

        @(starfieldPtr1) = 0
        @(starfieldPtr2) = 0
        @(starfieldPtr3) = 0
        @(starfieldPtr4) = 0

        if rasterCount & 1 {
            starfieldPtr1++
            if starfieldPtr1==star1Limit
                starfieldPtr1=star1Reset
        }
        starfieldPtr2++
        if starfieldPtr2==star2Limit
            starfieldPtr2=star2Reset
        if rasterCount & 3 == 0 {
            starfieldPtr3++
            if starfieldPtr3==star3Limit
                starfieldPtr3=star3Reset
        }
        starfieldPtr4 += 2
        if starfieldPtr4==star4Limit
            starfieldPtr4=star4Reset

        ; 2 static stars that flicker
        if rasterCount >= 230
            @(staticStar1) = 0
        else
            @(staticStar1) = 192
        if rasterCount ^ $80 >= 230
            @(staticStar2) = 0
        else
            @(staticStar2) = 192

        ; Plot new stars
        @(starfieldPtr1) |= 3
        @(starfieldPtr2) |= 3
        @(starfieldPtr3) |= 12
        @(starfieldPtr4) |= 48
    }

    sub initStarfield() {
        starfieldPtr1 = star1Init
        starfieldPtr2 = star2Init
        starfieldPtr3 = star3Init
        starfieldPtr4 = star4Init
    }

    sub createStarScreen() {
        ; Creates the starfield charmap and colour charmap
        ; This routine paints vertical stripes of colour into the colourmap
        ; so the stars are different colours
        ; It also plots the correct characters to the screen, wrapping them around
        ; at the correct char count to give to the starfield effect.

        uword @zp ptr
        ubyte x
        for x in 0 to 39 {
            ubyte limit
            ubyte char = starfieldRow[x]
            if char >= 58+25
                limit = 58+50
            else
                limit = 58+25
            ubyte start = limit - 25
            ptr = starScreenChar
            repeat 25 {
                ptr[x] = char
                ptr += 40
                char ++
                if char == limit
                    char = start
            }
        }

        ; Fill colour map with vertical stripes of colour for starfield
        ptr = StarScreenCols
        ubyte ci = 0
        repeat 25 {
            for x in 0 to 39 {
                ptr[x] = starfieldCols[ci]
                ci ++
                if ci==starColourLimit
                    ci=0
            }
            ptr += 40
        }
    }

    ; Dark starfield so it doesnt distract from bullets and text
    ubyte[20] starfieldCols = [
        14,10,12,15,14,13,12,11,10,14,
        14,10,14,15,14,13,12,11,10,12
        ]

    ; Star positions, 40 X positions, range 58-107
    ubyte[40] starfieldRow = [
        058,092,073,064,091,062,093,081,066,094,
        086,059,079,087,080,071,076,067,082,095,
        100,078,099,060,075,063,084,065,083,096,
        068,088,074,061,090,098,085,101,097,077
        ]
}
