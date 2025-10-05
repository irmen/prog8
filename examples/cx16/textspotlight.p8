%import textio

main {
    sub start() {
        lorumipsum()
        txt.t256c(true)

        ubyte x=5
        ubyte y=8
        byte dx=1
        byte dy=1

        repeat {
            colorize(x,y)
            sys.waitvsync()
            sys.waitvsync()
            sys.waitvsync()
            black(x,y)

            x += dx as ubyte
            if_neg {
                x = 1
                dx = 1
            } else if x==txt.DEFAULT_WIDTH-30 {
                x = txt.DEFAULT_WIDTH-31
                dx = -1
            }

            y += dy as ubyte
            if_neg {
                y = 1
                dy = 1
            } else if y==txt.DEFAULT_HEIGHT-30 {
                y = txt.DEFAULT_HEIGHT-31
                dy = -1
            }
        }
    }

    sub black(ubyte xx, ubyte yy) {
        uword vera_offset = (txt.VERA_TEXTMATRIX & $ffff) + xx*2 + 1 + yy*256
        repeat 31 {
            cx16.vaddr_autoincr(msw(txt.VERA_TEXTMATRIX), vera_offset, 0, 2)
            unroll 31 cx16.VERA_DATA0 = 0
            vera_offset += 256
        }
    }

    sub colorize(ubyte xx, ubyte yy) {
        ^^ubyte colorptr = &colors
        uword vera_offset = (txt.VERA_TEXTMATRIX & $ffff) + xx*2 + 1 + yy*256
        repeat 31 {
            cx16.vaddr_autoincr(msw(txt.VERA_TEXTMATRIX), vera_offset, 0, 2)
            repeat 31 {
                cx16.VERA_DATA0 = @(colorptr)
                colorptr ++
            }
            vera_offset += 256
        }
    }

    sub lorumipsum() {
        txt.lowercase()
        txt.color(0)
        repeat 3 {
            txt.print("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Maecenas ultricies ex massa, sit amet aliquet risus aliquet vitae. ")
            txt.print("Sed sit amet turpis et augue ultrices rhoncus at vel metus. Nunc malesuada felis in libero sodales pulvinar. Donec rutrum est sed luctus sodales. ")
            txt.print("Sed egestas faucibus purus, fermentum porttitor arcu cursus ac. Vivamus fermentum et justo eget cursus. Pellentesque accumsan ultrices placerat. ")
            txt.print("Fusce dapibus ut orci a posuere. Mauris tristique eget orci ut feugiat. Suspendisse eget leo semper, condimentum nisl a, tempus turpis. ")
            txt.print("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin ultrices, tellus ac bibendum vulputate, enim nulla bibendum massa, ")
            txt.print("ultrices lobortis quam enim eget sem. Cras in metus elit.\n\n")
            txt.print("Quisque convallis leo metus, at luctus elit pretium mollis. Aliquam quis tellus est. Vestibulum rutrum lorem vel luctus posuere. ")
            txt.print("Aliquam finibus eros sit amet eleifend sodales. Phasellus at viverra nisi. Suspendisse potenti. Sed sem quam, rhoncus dictum urna eu, malesuada pretium libero. ")
            txt.print("Integer quis odio nulla. Fusce luctus vulputate quam id tempor. Etiam eget nisl leo. Morbi fermentum ullamcorper tellus id euismod.\n\n")
            txt.print("Nam scelerisque ante ut pharetra pretium. In vitae vehicula sem, eget consequat dolor. Vestibulum mollis libero diam, id molestie est ullamcorper sed. ")
            txt.print("Aenean gravida tortor a orci rutrum sodales. Cras mollis iaculis ipsum id sollicitudin. Mauris at elit vitae odio tempor efficitur. ")
            txt.print("Curabitur convallis turpis vitae diam elementum vestibulum.\n\n")
        }
    }

    colors:
        ; 31 x 31 cell colors. This is too large for a native prog8 array so we use an inline assembly block

    %asm {{
        .byte $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $10, $10, $10, $10, $10, $10, $10, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
        .byte $00, $00, $00, $00, $00, $00, $00, $00, $00, $10, $10, $10, $11, $11, $11, $11, $11, $11, $11, $10, $10, $10, $00, $00, $00, $00, $00, $00, $00, $00, $00
        .byte $00, $00, $00, $00, $00, $00, $00, $10, $10, $11, $11, $11, $12, $12, $12, $12, $12, $12, $12, $11, $11, $11, $10, $10, $00, $00, $00, $00, $00, $00, $00
        .byte $00, $00, $00, $00, $00, $00, $10, $10, $11, $12, $12, $12, $13, $13, $13, $13, $13, $13, $13, $12, $12, $12, $11, $10, $10, $00, $00, $00, $00, $00, $00
        .byte $00, $00, $00, $00, $00, $10, $11, $11, $12, $12, $13, $13, $14, $14, $14, $14, $14, $14, $14, $13, $13, $12, $12, $11, $11, $10, $00, $00, $00, $00, $00
        .byte $00, $00, $00, $00, $10, $11, $11, $12, $13, $13, $14, $14, $15, $15, $15, $15, $15, $15, $15, $14, $14, $13, $13, $12, $11, $11, $10, $00, $00, $00, $00
        .byte $00, $00, $00, $10, $11, $11, $12, $13, $13, $14, $15, $15, $15, $16, $16, $16, $16, $16, $15, $15, $15, $14, $13, $13, $12, $11, $11, $10, $00, $00, $00
        .byte $00, $00, $10, $10, $11, $12, $13, $14, $14, $15, $15, $16, $16, $17, $17, $17, $17, $17, $16, $16, $15, $15, $14, $14, $13, $12, $11, $10, $10, $00, $00
        .byte $00, $00, $10, $11, $12, $13, $13, $14, $15, $16, $16, $17, $17, $18, $18, $18, $18, $18, $17, $17, $16, $16, $15, $14, $13, $13, $12, $11, $10, $00, $00
        .byte $00, $10, $11, $12, $12, $13, $14, $15, $16, $16, $17, $18, $18, $19, $19, $19, $19, $19, $18, $18, $17, $16, $16, $15, $14, $13, $12, $12, $11, $10, $00
        .byte $00, $10, $11, $12, $13, $14, $15, $15, $16, $17, $18, $18, $19, $1A, $1A, $1A, $1A, $1A, $19, $18, $18, $17, $16, $15, $15, $14, $13, $12, $11, $10, $00
        .byte $00, $10, $11, $12, $13, $14, $15, $16, $17, $18, $18, $19, $1A, $1A, $1B, $1B, $1B, $1A, $1A, $19, $18, $18, $17, $16, $15, $14, $13, $12, $11, $10, $00
        .byte $10, $11, $12, $13, $14, $15, $15, $16, $17, $18, $19, $1A, $1B, $1B, $1C, $1C, $1C, $1B, $1B, $1A, $19, $18, $17, $16, $15, $15, $14, $13, $12, $11, $10
        .byte $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $1A, $1A, $1B, $1C, $1D, $1D, $1D, $1C, $1B, $1A, $1A, $19, $18, $17, $16, $15, $14, $13, $12, $11, $10
        .byte $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $1A, $1B, $1C, $1D, $1D, $1E, $1D, $1D, $1C, $1B, $1A, $19, $18, $17, $16, $15, $14, $13, $12, $11, $10
        .byte $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $1A, $1B, $1C, $1D, $1E, $01, $1E, $1D, $1C, $1B, $1A, $19, $18, $17, $16, $15, $14, $13, $12, $11, $10
        .byte $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $1A, $1B, $1C, $1D, $1D, $1E, $1D, $1D, $1C, $1B, $1A, $19, $18, $17, $16, $15, $14, $13, $12, $11, $10
        .byte $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $1A, $1A, $1B, $1C, $1D, $1D, $1D, $1C, $1B, $1A, $1A, $19, $18, $17, $16, $15, $14, $13, $12, $11, $10
        .byte $10, $11, $12, $13, $14, $15, $15, $16, $17, $18, $19, $1A, $1B, $1B, $1C, $1C, $1C, $1B, $1B, $1A, $19, $18, $17, $16, $15, $15, $14, $13, $12, $11, $10
        .byte $00, $10, $11, $12, $13, $14, $15, $16, $17, $18, $18, $19, $1A, $1A, $1B, $1B, $1B, $1A, $1A, $19, $18, $18, $17, $16, $15, $14, $13, $12, $11, $10, $00
        .byte $00, $10, $11, $12, $13, $14, $15, $15, $16, $17, $18, $18, $19, $1A, $1A, $1A, $1A, $1A, $19, $18, $18, $17, $16, $15, $15, $14, $13, $12, $11, $10, $00
        .byte $00, $10, $11, $12, $12, $13, $14, $15, $16, $16, $17, $18, $18, $19, $19, $19, $19, $19, $18, $18, $17, $16, $16, $15, $14, $13, $12, $12, $11, $10, $00
        .byte $00, $00, $10, $11, $12, $13, $13, $14, $15, $16, $16, $17, $17, $18, $18, $18, $18, $18, $17, $17, $16, $16, $15, $14, $13, $13, $12, $11, $10, $00, $00
        .byte $00, $00, $10, $10, $11, $12, $13, $14, $14, $15, $15, $16, $16, $17, $17, $17, $17, $17, $16, $16, $15, $15, $14, $14, $13, $12, $11, $10, $10, $00, $00
        .byte $00, $00, $00, $10, $11, $11, $12, $13, $13, $14, $15, $15, $15, $16, $16, $16, $16, $16, $15, $15, $15, $14, $13, $13, $12, $11, $11, $10, $00, $00, $00
        .byte $00, $00, $00, $00, $10, $11, $11, $12, $13, $13, $14, $14, $15, $15, $15, $15, $15, $15, $15, $14, $14, $13, $13, $12, $11, $11, $10, $00, $00, $00, $00
        .byte $00, $00, $00, $00, $00, $10, $11, $11, $12, $12, $13, $13, $14, $14, $14, $14, $14, $14, $14, $13, $13, $12, $12, $11, $11, $10, $00, $00, $00, $00, $00
        .byte $00, $00, $00, $00, $00, $00, $10, $10, $11, $12, $12, $12, $13, $13, $13, $13, $13, $13, $13, $12, $12, $12, $11, $10, $10, $00, $00, $00, $00, $00, $00
        .byte $00, $00, $00, $00, $00, $00, $00, $10, $10, $11, $11, $11, $12, $12, $12, $12, $12, $12, $12, $11, $11, $11, $10, $10, $00, $00, $00, $00, $00, $00, $00
        .byte $00, $00, $00, $00, $00, $00, $00, $00, $00, $10, $10, $10, $11, $11, $11, $11, $11, $11, $11, $10, $10, $10, $00, $00, $00, $00, $00, $00, $00, $00, $00
        .byte $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $10, $10, $10, $10, $10, $10, $10, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
    }}
}
