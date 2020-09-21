%import syslib
; %import graphics
%import textio
; %zeropage basicsafe


main {

    sub start()  {

        ubyte x
        ubyte y
;        for y in 0 to txt.DEFAULT_HEIGHT-1 {
;            for x in 0 to txt.DEFAULT_WIDTH-1 {
;                txt.setchr(x,y,x+y)
;            }
;        }

        repeat txt.DEFAULT_WIDTH {
            txt.setcc(txt.DEFAULT_WIDTH-1, rnd() % txt.DEFAULT_HEIGHT, 81, 2)
            txt.scroll_left(true)

            repeat 5000 {
                x++
            }
        }
    }
}
