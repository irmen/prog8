%import syslib
; %import graphics
%import textio
%zeropage basicsafe


main {

    sub start()  {

        ubyte x
        ubyte y
;        for y in 0 to txt.DEFAULT_HEIGHT-1 {
;            for x in 0 to txt.DEFAULT_WIDTH-1 {
;                txt.setchr(x,y,x+y)
;            }
;        }

        repeat 60 {
            txt.setcc(rnd() % 80, 59, 81, 5)
            txt.scroll_up(true)

            repeat 5000 {
                x++
            }
        }
    }
}
