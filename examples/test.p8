%import textio
%import floats

main {

    sub start() {

        ubyte xx = 1.234
        ubyte yy = 2.234
        uword aw
        byte bb
        float fl

        ; TODO:  bitwise operations with a negative constant number -> replace the number by its positive 2 complement

; (X + C1) + (Y + C2)  =>  (X + Y) + (C1 + C2)
; (X + C1) - (Y + C2)  =>  (X - Y) + (C1 - C2)
; ---> together:   (X + C1) <plusmin> (Y + C2)  =>  (X <plusmin> Y) + (C1 <plusmin> C2)


; (X - C1) + (Y - C2)  =>  (X + Y) - (C1 + C2)
; (X - C1) - (Y - C2)  =>  (X - Y) - (C1 - C2)

        xx=6
        yy=8


        yy = (xx+5)+(yy+10)
        ; yy = (xx+yy)+(5+10)     ; TODO crashes compiler
        txt.print_ub(yy)        ; 29
        txt.nl()

        xx=100
        yy=8
        ;yy = (xx+5)-(yy+10)
        yy = (xx-yy)+(5-10) as ubyte
        txt.print_ub(yy)        ; 87
        txt.nl()

        xx=50
        yy=40
        yy = (xx-5)+(yy-10)
        txt.print_ub(yy)        ; 75
        txt.nl()

        xx=50
        yy=20
        yy = (xx-5)-(yy-10)
        txt.print_ub(yy)        ; 35
        txt.nl()

        repeat {
        }
    }
}
