;%import c64utils
%option enable_floats

~ main  {


sub start() {

    memory word  mword = $c000
    memory word  mword2 = $c200
    memory uword muword = $c100
    memory uword muword2 = $c300
    ubyte ubvar
    byte bvar
    memory ubyte mubvar = $c400
    float fvar
    uword uwvar
    ubyte[10] barray


    lsl(Y)
    lsl(ubvar)
    lsl(mubvar)
    lsl(uwvar)
    lsl(barray[2])


;    lsr(Y)
;    rol(Y)
;    ror(Y)
;    ror2(Y)
;
;    lsl(bvar)
;    lsr(bvar)
;    rol(bvar)
;    ror(bvar)
;    ror2(bvar)
;
;    lsl(mubvar)
;    lsr(mubvar)
;    rol(mubvar)
;    ror(mubvar)
;    ror2(mubvar)


;    lsl(XY)
;    lsr(XY)
;    rol(XY)
;    ror(XY)
;    ror2(XY)
;
;    lsl(uwvar)
;    lsr(uwvar)
;    rol(uwvar)
;    ror(uwvar)
;    ror2(uwvar)


    return


}

}
