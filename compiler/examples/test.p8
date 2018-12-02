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


    float[10]  farr
    float f = 3.3
    memory float mflt = $c000
    memory float[10] mfarr = $d000
    ubyte i=3

    mflt = 55.44            ; @todo fix memory variables for stackvm???! (or proper error)
    mfarr[2] = 44.44        ; @todo fix memory variables for stackvm???! (or proper error)

    farr[2] = 4.44
    farr[2] = f
    farr[2] = mflt
    farr[2] = farr[3]
    farr[2] = mfarr[3]
    farr[Y] = 4.44
    farr[Y] = f
    farr[Y] = mflt
    farr[Y] = farr[3]
    farr[Y] = mfarr[3]
    farr[i] = 4.44
    farr[i] = f
    farr[i] = mflt
    farr[i] = farr[3]
    farr[i] = mfarr[3]


    return
}


}
