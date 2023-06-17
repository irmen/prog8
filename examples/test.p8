%import math
%import textio
%zeropage basicsafe

main  {

    const ubyte WIDTH=255
    const ubyte HEIGHT=240

    sub start() {
        sys.gfx_enable(0)       ; enable lo res screen
        ;; gfx2.screen_mode(4)
        repeat {

            ubyte xx
            ubyte yy

            for yy in 0 to HEIGHT-1 {
                for xx in 0 to WIDTH-1 {
                    ubyte value = math.direction(WIDTH/2, HEIGHT/2, xx, yy)
                    ;; gfx2.plot(xx,yy,value)
                    sys.gfx_plot(xx, yy, value*10)
                }
            }
        }
    }
}


;main {
;
;    sub start() {
;
;        const ubyte HEIGHT = txt.DEFAULT_HEIGHT
;        const ubyte WIDTH = txt.DEFAULT_WIDTH
;        const ubyte HALFWIDTH = txt.DEFAULT_WIDTH/2
;        const ubyte HALFHEIGHT = txt.DEFAULT_HEIGHT/2
;
;        ubyte @zp value
;        ubyte xx
;        ubyte yy
;;        for yy in 0 to HEIGHT-1 {
;;            for xx in 0 to WIDTH-1 {
;;                value = math.atan(HALFWIDTH, HALFHEIGHT, xx, yy)
;;                txt.setchr(xx,yy,value)
;;            }
;;        }
;;
;;        byte sx
;;        byte sy
;;        for sy in -HEIGHT/2 to HEIGHT/2  {
;;            for sx in -WIDTH/2 to WIDTH/2 {
;;                value = math.direction_sc(0, 0, sx, sy)
;;                txt.setchr(sx+WIDTH/2 as ubyte,sy+HEIGHT/2 as ubyte,value)
;;            }
;;        }
;
;        for yy in 0 to HEIGHT-1 {
;            for xx in 0 to WIDTH-1 {
;                value = math.direction(HALFWIDTH, HALFHEIGHT, xx, yy)
;                txt.setchr(xx,yy,value)
;            }
;        }
;
;        goto start
;    }
;}
