;%import c64utils
%option enable_floats

~ main  {


sub start() {

    byte bvar
    ubyte ubvar
    memory byte mbvar = $c400
    memory ubyte mubvar = $c400
    byte[10] barray
    ubyte[10] ubarray

    memory word  mword = $c000
    memory word  mword2 = $c200
    memory uword muword = $c100
    memory uword muword2 = $c300
    float fvar
    uword uwvar
    word wvar
    uword[10] uwarray


    lsl(Y)
    lsl(ubvar)
    lsl(mubvar)
    lsl(ubarray[2])

    lsl(AY)
    lsl(uwvar)
    lsl(muword)
    lsl(uwarray[3])

    lsr(Y)
    lsr(ubvar)
    lsr(mubvar)
    lsr(ubarray[2])

    lsr(AY)
    lsr(uwvar)
    lsr(muword)
    lsr(uwarray[3])

    rol(Y)
    rol(ubvar)
    rol(mubvar)
    rol(ubarray[2])

    rol(AY)
    rol(uwvar)
    rol(muword)
    rol(uwarray[3])

    ror(Y)
    ror(ubvar)
    ror(mubvar)
    ror(ubarray[2])

    ror(AY)
    ror(uwvar)
    ror(muword)
    ror(uwarray[3])

    rol2(Y)
    rol2(ubvar)
    rol2(mubvar)
    rol2(ubarray[2])

    rol2(AY)
    rol2(uwvar)
    rol2(muword)
    rol2(uwarray[3])

    ror2(Y)
    ror2(ubvar)
    ror2(mubvar)
    ror2(ubarray[2])

    ror2(AY)
    ror2(uwvar)
    ror2(muword)
    ror2(uwarray[3])


    return


}

}
