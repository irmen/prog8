%import c64utils
%option enable_floats

~ main  {

sub start() {

;    memory ubyte[40] firstScreenLine = $0400
;    word ww=333
;
;    Y=33
;
;    if(Y>3) {
;        A=99
;    } else {
;        A=100
;    }
;
;    if(ww>33) {
;        A=99
;    } else {
;        A=100
;    }
;
;    for ubyte c in 10 to 30 {
;        firstScreenLine[c] = c
;    }


    ubyte bv = 99
    word wv = 14443
    float fv = 3.14
    memory ubyte mb = $c000
    memory word mw = $c100
    memory float mf = $c200
    memory ubyte[10] mba = $d000
    memory word[10] mwa = $d100
    memory float[10] mfa = $d200
    memory str mstr = $d300
    memory str_p mstrp = $d300
    memory str_s mstrs = $d300
    memory str_ps mstrps = $d300

    mb = 5
    mb = Y
    mb = bv
    mw = 22334
    mw = wv
    mf = 4.45
    mf = fv
    mba[9] = 5
    mba[9] = Y
    mba[9] = bv
    mwa[9] = 22334
    mwa[9] = wv
    mfa[9] = 5.667
    mfa[9] = fv

    return
}


}
