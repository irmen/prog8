%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub rrrr() -> ubyte {
        cx16.r0L++
        return cx16.r0L
    }

    sub start() {
        cx16.r0L = rrrr() >= 128

;        ubyte[] flakes = [1,2,3]
;
;        ubyte @shared idx = 2
;
;        if flakes[idx]==239 {
;            txt.print("yes")
;        } else {
;            txt.print("nope")
;        }
;
;        ubyte @shared xx = 16
;        ubyte @shared yy = 20
;
;        txt.print_ub(xx>79 or yy > 49)

        ; if xx>79 or yy > 49 {
;        if xx>79 or yy > 49 {
;            txt.print("no\n")
;        }
;        else {
;            txt.print("yes\n")
;        }
    }
}
