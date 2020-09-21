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

        repeat 999 {
            ubyte xpos = rnd() % (width-1)
            txt.setcc(xpos, 0, 81, 6)
            ubyte ypos = rnd() % (height-1)+1
            txt.setcc(width-1, ypos, 81, 2)
            txt.scroll_left(true)
            txt.scroll_down(true)
            repeat 2000 {
                x++
            }
        }
    }
}
