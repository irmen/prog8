%option enable_floats

~ main {

sub start() {

    memory ubyte mub = $c400
    memory ubyte mub2 = $c401
    memory uword muw = $c500
    memory uword muw2 = $c502
    memory byte mb = $c000
    memory word mw = $c002

    byte b
    ubyte ub = $c4
    ubyte ub2 = $c4
    uword uw = $c500
    uword uw2 = $c502
    word ww


    mub=5
    muw=4444
    mb=-100
    mw=-23333


;    b++
;    A++
;    XY++
;    mub++
;    ub++
;    uw++
;    ww++
;    mb++
;    mw++
;
;    b--
;    A--
;    XY--
;    mub--
;    ub--
;    uw--
;    ww--
;    mb--
;    mw--


    return

}

}
