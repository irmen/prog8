%import math
%import textio
%zeropage basicsafe

main {
    sub start() {
        cx16.r0sL = 127
        cx16.r0sL = bytefunc(cx16.r0sL+1)
        cx16.r0sL = 0
        cx16.r0sL = bytefunc(cx16.r0sL-1)
        cx16.r0sL = 55
        cx16.r0sL = bytefunc(cx16.r0sL+20)
        cx16.r0sL = 55
        cx16.r0sL = bytefunc(cx16.r0sL-20)

        cx16.r0s = $99ff as word
        cx16.r0s = wordfunc(cx16.r0s+1)
        cx16.r0s = $9900 as word
        cx16.r0s = wordfunc(cx16.r0s-1)
        cx16.r0s = -12345
        cx16.r0s = wordfunc(cx16.r0s+100)
        cx16.r0s = -12345
        cx16.r0s = wordfunc(cx16.r0s-100)
    }


    sub bytefunc(byte x) -> byte {
        txt.print_ubhex(x as ubyte, true)
        txt.spc()
        txt.print_b(x)
        txt.nl()
        return x
    }

    sub wordfunc(word x) -> word {
        txt.print_uwhex(x as uword, true)
        txt.spc()
        txt.print_w(x)
        txt.nl()
        return x
    }
}

;%import math
;%import sprites
;
;main {
;    word[128] @split xpos_orig
;    word[128] @split ypos_orig
;    word[128] xpos
;    word[128] ypos
;    ubyte[128] tt
;
;    sub start() {
;        cx16.mouse_config2(1)
;        sprites.set_mousepointer_hand()
;        ubyte sprdat_bank
;        uword sprdat_addr
;        sprdat_bank, sprdat_addr = sprites.get_data_ptr(0)
;
;        ubyte sprite
;        for sprite in 0 to 127 {
;            sprites.init(sprite, sprdat_bank, sprdat_addr, sprites.SIZE_16, sprites.SIZE_16, sprites.COLORS_256, 0)
;            xpos_orig[sprite] = sprite*$0003 +100 as word
;            ypos_orig[sprite] = sprite*$0002 +100 as word
;            tt[sprite] = math.rnd()
;        }
;
;        repeat {
;            sys.waitvsync()
;            sprites.pos_batch(0, 128, &xpos, &ypos)
;            for sprite in 0 to 127 {
;                tt[sprite]++
;                xpos[sprite] = xpos_orig[sprite] + math.sin8(tt[sprite])
;                ypos[sprite] = ypos_orig[sprite] + math.cos8(tt[sprite])
;            }
;        }
;    }
;}
;
;
;;%import textio
;;%zeropage basicsafe
;;%option no_sysinit
;;
;;main {
;;    sub start() {
;;        signed()
;;        unsigned()
;;    }
;;
;;    sub signed() {
;;        byte @shared bvalue = -100
;;        word @shared wvalue = -20000
;;
;;        bvalue /= 2     ; TODO should be a simple bit shift?
;;        wvalue /= 2     ; TODO should be a simple bit shift?
;;
;;        txt.print_b(bvalue)
;;        txt.nl()
;;        txt.print_w(wvalue)
;;        txt.nl()
;;
;;        bvalue *= 2
;;        wvalue *= 2
;;
;;        txt.print_b(bvalue)
;;        txt.nl()
;;        txt.print_w(wvalue)
;;        txt.nl()
;;    }
;;
;;    sub unsigned() {
;;        ubyte @shared ubvalue = 100
;;        uword @shared uwvalue = 20000
;;
;;        ubvalue /= 2
;;        uwvalue /= 2
;;
;;        txt.print_ub(ubvalue)
;;        txt.nl()
;;        txt.print_uw(uwvalue)
;;        txt.nl()
;;
;;        ubvalue *= 2
;;        uwvalue *= 2
;;
;;        txt.print_ub(ubvalue)
;;        txt.nl()
;;        txt.print_uw(uwvalue)
;;        txt.nl()
;;    }
;;}
