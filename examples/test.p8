%import textio
%import conv

main {

    sub start() {

        uword xx
        ubyte yy = 33
        void sin8u(yy)
        ; concat_string(random_name())

;        ubyte  xx=20
;        ubyte  yy=10
;
;        routine(33)
;        txt.setcc(xx+1, yy+3, 81, 7)
;        txt.setcc(xx+2, yy+2, 81, 7)
;        txt.setcc(xx+3, yy+1, 81, 7)
;
;        ; TODO test new param load with subroutine call in expression:
;        ; yy=routine(33)
;
;        main.routine.r1arg = 20
;        ; main.routine2.r2arg = 20      ; TODO asmgen
;
;        xx = main.routine.r1arg
;        xx++
;        ;xx = main.routine2.r2arg           ; TODO asmgen
;        ;xx++

        repeat {
        }
    }

    sub random_name() -> str {
        ubyte ii
        str name = "        "       ; 8 chars max
        return name
    }

    sub routine(ubyte r1arg) -> ubyte {
        r1arg++
        return r1arg
    }

    asmsub routine2(ubyte r2arg @ A) {
        %asm {{
            rts
        }}
    }

}
