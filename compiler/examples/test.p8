%option enable_floats

~ main {

sub start() {

    memory ubyte mub = $c400
    memory ubyte mub2 = $c401
    memory uword muw = $c500
    memory uword muw2 = $c502
    memory byte mb = $c000
    memory word mw = $c002


    byte b = -100
    ubyte ub = $c4
    ubyte ub2 = $c4
    uword uw = $c500
    uword uw2 = $c502
    word ww = -30000

    float f1 = 1.23456
    float f2 = -999.999e22
    float f3 = -999.999e22
    float f4 = -999.999e22

    str s1 = "hallo"
    str_p s2 = "hallo p"
    str_s s3 = "hallo s"
    str_ps s4 = "hallo ps"

    byte[4] array1 = -99
    ubyte[4] array2 = 244
    word[4] array3 = -999
    uword[4] array4 = 44444
    float[4] array5 = [11.11, 22.22, 33.33, 44.44]
    byte[3,7] matrix1 =  [1,-2,3,-4,5,-6,1,-2,3,-4,5,-6,1,-2,3,-4,5,-6,22,33,44]
    ubyte[3,7] matrix2 =  [11,22,33,44,55,66, 11,22,33,44,55,66, 11,22,33,44,55,66, 55,66,77]


    return

    sub nested () {
        byte b
        uword uw
    }
}

sub second() {
    byte b
    uword uw
}

}
