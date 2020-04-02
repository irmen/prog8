%import c64utils
%zeropage basicsafe

main {

    byte bb
    ubyte ub
    word ww
    uword uw
    &ubyte  membyte=9999
    &uword  memword=9999
    ubyte[] ubarray = [8,%10011100,8]
    uword[] uwarray = [8200, %1001110011110001, 8200]
    byte[] bbarray = [8,-%01011100,8]
    word[] wwarray = [8200, -%0101110011110001, 8200]

    sub unimplemented() {
        c64scr.print_ubbin(ubarray[1], false)
        c64.CHROUT('\n')
        lsr(ubarray[1])
        c64scr.print_ubbin(ubarray[1], false)
        c64.CHROUT('\n')
        lsr(ubarray[1])
        c64scr.print_ubbin(ubarray[1], false)
        c64.CHROUT('\n')
        lsr(ubarray[1])
        c64scr.print_ubbin(ubarray[1], false)
        c64.CHROUT('\n')
        lsr(ubarray[1])
        c64scr.print_ubbin(ubarray[1], false)
        c64.CHROUT('\n')
        lsr(ubarray[1])
        c64scr.print_ubbin(ubarray[1], false)
        c64.CHROUT('\n')
        lsr(ubarray[1])
        c64scr.print_ubbin(ubarray[1], false)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        c64scr.print_ubbin(bbarray[1] as ubyte, false)
        c64.CHROUT('\n')
        lsr(bbarray[1])
        c64scr.print_ubbin(bbarray[1] as ubyte, false)
        c64.CHROUT('\n')
        lsr(bbarray[1])
        c64scr.print_ubbin(bbarray[1] as ubyte, false)
        c64.CHROUT('\n')
        lsr(bbarray[1])
        c64scr.print_ubbin(bbarray[1] as ubyte, false)
        c64.CHROUT('\n')
        lsr(bbarray[1])
        c64scr.print_ubbin(bbarray[1] as ubyte, false)
        c64.CHROUT('\n')
        lsr(bbarray[1])
        c64scr.print_ubbin(bbarray[1] as ubyte, false)
        c64.CHROUT('\n')
        lsr(bbarray[1])
        c64scr.print_ubbin(bbarray[1] as ubyte, false)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        c64scr.print_uwbin(uwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(uwarray[1])
        c64scr.print_uwbin(uwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(uwarray[1])
        c64scr.print_uwbin(uwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(uwarray[1])
        c64scr.print_uwbin(uwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(uwarray[1])
        c64scr.print_uwbin(uwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(uwarray[1])
        c64scr.print_uwbin(uwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(uwarray[1])
        c64scr.print_uwbin(uwarray[1] as uword, false)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        c64scr.print_uwbin(wwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(wwarray[1])
        c64scr.print_uwbin(wwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(wwarray[1])
        c64scr.print_uwbin(wwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(wwarray[1])
        c64scr.print_uwbin(wwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(wwarray[1])
        c64scr.print_uwbin(wwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(wwarray[1])
        c64scr.print_uwbin(wwarray[1] as uword, false)
        c64.CHROUT('\n')
        lsr(wwarray[1])
        c64scr.print_uwbin(wwarray[1] as uword, false)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

;        lsl(ubarray[1])
;        ror(ubarray[1])
;        rol(ubarray[1])
;        ror2(ubarray[1])
;        rol2(ubarray[1])
;        lsl(bbarray[1])
;
;        lsl(uwarray[1])
;        ror(uwarray[1])
;        rol(uwarray[1])
;        ror2(uwarray[1])
;        rol2(uwarray[1])
;        lsr(wwarray[1])
;        lsl(wwarray[1])
    }

    sub start() {
        unimplemented()

        lsr(A)
        lsl(A)
        ror(A)
        rol(A)
        ror2(A)
        rol2(A)
        lsr(Y)
        lsl(Y)
        ror(Y)
        rol(Y)
        ror2(Y)
        rol2(Y)

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

        check_eval_stack()
    }

    sub check_eval_stack() {
        if X!=255 {
            c64scr.print("x=")
            c64scr.print_ub(X)
            c64scr.print(" error!\n")
        }
    }

}
