%import textio
%import test_stack
%zeropage dontuse

main {

    sub start() {
        test_stack.test()

        ubyte[20] xpos = 19 to 0 step -1
        ubyte[20] ypos = 19 to 0 step -1

        ubyte ball
        for ball in 0 to len(xpos)-1 {
            ubyte xx = xpos[ball] + 1
            ubyte yy = ypos[ball]
            txt.setchr(xx,yy,87)        ; correct codegen
            txt.setclr(xx,yy,5)         ; correct codegen
            txt.setchr(xpos[ball], ypos[ball], 81)          ; TODO WRONG CODEGEN WITH NOOPT
            txt.setclr(xpos[ball], ypos[ball], 6)           ; TODO WRONG CODEGEN WITH NOOPT
        }

        test_stack.test()
        repeat {
        }

    }
}
