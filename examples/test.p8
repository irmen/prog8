%import syslib
; %import graphics
%import textio
%zeropage basicsafe


main {

    sub start()  {

        ; cx16.screen_set_mode(128)

        ubyte width = txt.width()
        ubyte height = txt.height()

        ubyte x
        ubyte y
        for y in 0 to height-1 {
            for x in 0 to width-1 {
                txt.setchr(x,y,x+y)
            }
        }

        repeat width {
            txt.setcc(width-1, rnd() % height, 81, 2)
            txt.scroll_left(true)

            repeat 1000 {
                x++
            }
        }
    }
}
