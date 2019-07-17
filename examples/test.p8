%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {

        word  w1
        word  w2
        word  w3
        word  w4
        word  w5
        word  w6
        word  w7
        word  w8
        word  w9
        word  w10
        word  w11
        word  w12
        word  w13
        word  w14
        word  w15
        word  w16
        word  w17
        word  w18
        word  w19
        word  @zp w20
        word  @zp w21
        word  w22
        word  w23
        word  w24
        word  w25
        word  w26
        word  w27
        word  w28
        word  w29
        word  w30

        ubyte[] blaat = 10 to 20

        for ubyte c in 'a' to 'z' {
            c64.CHROUT(blaat[c])
        }

        word q
        q=w26
        q+=w27
        q+=w28
        q+=w29
        q+=w30
        q+=w10
        q+=w11
        q+=w12
        q+=w13
        q+=w14
        q+=w15
        q+=w16
        q+=w17
        q+=w18
        q+=w19
        q+=w20
        q+=w21
        q+=w22
        q+=w23
        q+=w24
        q+=w25
        q+=w26
        q+=w27


    }

}
