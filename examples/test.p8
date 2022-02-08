%import textio
%zeropage basicsafe

main {
    ubyte @zp mainglobal1=10

    sub start() {
        c64.SETNAM(1, "$")
        txt.print("1")
        txt.print("12")
        txt.print("test.")
        txt.print("test.")
;        foobar(2,1,1,1)
;        foobar2(2,1,1,1)
    }

    sub unusedsubroutine() {
        c64.SETNAM(1, "$")      ; TODO fix don't remove this interned string because referenced in start()
        txt.print("this string should be removed from the pool")
        txt.print("this string should be removed from the pool")
        txt.print("this string should be removed from the pool")
    }

;    asmsub foobar2(ubyte argument @A, uword a2 @R0, uword a3 @R1, uword a4 @R2) -> ubyte @A {
;        %asm {{
;            lda  #0
;            rts
;        }}
;    }
;    sub foobar(ubyte argument, uword a2, uword a3, uword a4) -> ubyte {
;        argument++
;        return lsb(a2)
;    }
}

foobar {
    str name = "don't remove this one"

    sub unusedinfoobar() {
        name = "should be removed"                  ; TODO remove from interned strings because subroutine got removed
        txt.print(name)
        txt.print("should also be removed2")        ; TODO remove from interned strings because subroutine got removed
    }
}
