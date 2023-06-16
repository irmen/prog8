%import math
%import textio
%zeropage basicsafe

main {

    sub start() {

        const ubyte HEIGHT = 30 ; txt.DEFAULT_HEIGHT-1
        const ubyte WIDTH = 80 ; txt.DEFAULT_WIDTH-1
        const ubyte HALFWIDTH = 40 ; txt.DEFAULT_WIDTH/2
        const ubyte HALFHEIGHT = 15 ; txt.DEFAULT_HEIGHT/2

        txt.print_ub(math.atan(0, 0, 10, 20))

;        ubyte @zp value
;        ubyte xx
;        ubyte yy
;        for yy in 0 to HEIGHT {
;            for xx in 0 to WIDTH {
;                value = math.atan(HALFWIDTH, HALFHEIGHT, xx, yy)
;                txt.setchr(xx,yy,value)
;            }
;        }
;
;        byte sx
;        byte sy
;        for sy in 0 to HEIGHT as byte {
;            for sx in 0 to WIDTH as byte {
;                value = math.atan_coarse_sgn(0, 0, sx-HALFWIDTH, sy-HALFHEIGHT)
;                txt.setchr(sx as ubyte,sy as ubyte,value)
;            }
;        }
;
;        for yy in 0 to HEIGHT {
;            for xx in 0 to WIDTH {
;                value = math.atan_coarse(HALFWIDTH, HALFHEIGHT, xx, yy)
;                txt.setchr(xx,yy,value)
;            }
;        }
;
;        goto start
    }
}
