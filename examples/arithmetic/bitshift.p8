%import c64utils
%zeropage basicsafe

; TODO implement asm generation for all operation in here

main {

    byte bb
    ubyte ub
    word ww
    uword uw
    &ubyte  membyte=9999
    &uword  memword=9999
    ubyte[10] ubarray
    byte[10] bbarray

    sub start() {
;        lsr(A)
;        lsl(A)
;        ror(A)
;        rol(A)
;        ror2(A)
;        rol2(A)

        lsr(bb)
        lsl(bb)

        lsr(membyte)
        lsl(membyte)
        ror(membyte)
        rol(membyte)
        ror2(membyte)
        rol2(membyte)
        lsr(memword)
        lsl(memword)
        ror(memword)
        rol(memword)
        ror2(memword)
        rol2(memword)

        lsl(@(9999))
        lsr(@(9999))
        ror(@(9999))
        rol(@(9999))
        ror2(@(9999))
        rol2(@(9999))

        lsl(@(9999+A))
        lsr(@(9999+A))
        ror(@(9999+A))
        rol(@(9999+A))
        ror2(@(9999+A))
        rol2(@(9999+A))

        lsr(ubarray[1])
        lsl(ubarray[1])
        ror(ubarray[1])
        rol(ubarray[1])
        ror2(ubarray[1])
        rol2(ubarray[1])
        lsr(bbarray[1])
        lsl(bbarray[1])


        bb /= 2
        bb >>= 1
        bb *= 4
        bb <<= 2

        ub /= 2
        ub >>= 1
        ub *= 4
        ub <<= 2
        rol(ub)
        ror(ub)
        rol2(ub)
        ror2(ub)

        ww /= 2
        ww >>= 1
        ww *= 4
        ww <<= 2

        uw /= 2
        uw >>= 1
        uw *= 4
        uw <<= 2
        rol(uw)
        ror(uw)
        rol2(uw)
        ror2(uw)
    }
}
