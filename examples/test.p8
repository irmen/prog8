%import textio
%import diskio
%import floats
%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {

    romsub $ff2c = GRAPH_draw_line(uword x1 @R0, uword y1 @R1, uword x2 @R2, uword y2 @R3)  clobbers(A,X,Y)       ; uses x1=r0, y1=r1, x2=r2, y2=r3

    sub start () {
        GRAPH_draw_line(1,2,3,4)
    }
}
