%import c64utils
%import c64flt

~ main {

    byte[3] stuff=[1,2,3]
    ubyte ub
    word w
    uword uw

    sub start()  {

        A //=  1            ; @todo wrong error about float
        A = A//1           ; @todo wrong error about float
        Y = A//1           ; @todo wrong error about float


        A=A*2
        A=255
        A=A*4
        A=255
        A=A*8
        A=255
        A=A*16
        A=255
        A=A*32
        A=255
        A=A*64
        A=255
        A=A*128
        A=255
        A=A*3
        A=255

;        A *= 0
;        A *= 1
;        A *= 2
;        A *= 3
;        A *= 4
;        A *= 5
;        A *= 6
;        A *= 7
;        A *= 8
;        A *= 9
;        A *= 10
;        A *= 11
;        A *= 16
;        A *= 32
;        A *= 64
;        A *= 128
;        A *= 255
        ;A *= 256
        ;A *= 257

;        Y = A
;        Y = A*0
;        Y = A*1
;        Y = A*2
;        Y = A*3
;        Y = A*4
;        Y = A*5
;        Y = A*6
;        Y = A*7
;        Y = A*8
;        Y = A*9
;        Y = A*10
;        Y = A*11
;        Y = A*16
;        Y = A*32
;        Y = A*64
;        Y = A*128
;        Y = A*255
        ;Y = A*256
        ;Y = A*257
    }
}
