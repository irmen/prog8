%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {
    sub start() {
        txt.fill_screen('.',2)

        ubyte xx
        ubyte yy = 0
        for xx in 0 to txt.DEFAULT_WIDTH-1 {
            txt.setcc(xx, 0, xx, 1)
            txt.setcc(xx, txt.DEFAULT_HEIGHT-1, xx, 1)
        }
        for yy in 0 to txt.DEFAULT_HEIGHT-1 {
            txt.setcc(0, yy, yy,1)
            txt.setcc(txt.DEFAULT_WIDTH-1, yy, yy, 1)
        }

        repeat {
            delay()
            txt.scroll_left(false)
        }
    }


    sub delay () {
        ubyte tt
        repeat 255 {
            repeat 255 {
                tt++
            }
        }
    }
}
