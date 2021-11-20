%import textio

main {

    sub start() {

        ubyte xx = 1
        ubyte yy = 2

;; TODO add these constant folders:
;
;; (X + C1) + (Y + C2)  =>  (X + Y) + (C1 + C2)
;; (X + C1) - (Y + C2)  =>  (X - Y) + (C1 - C2)
;; ---> together:   (X + C1) <plusmin> (Y + C2)  =>  (X <plusmin> Y) + (C1 <plusmin> C2)

;; (X * C) + (Y * C)  =>  (X + Y) * C
;; (X * C) - (Y * C)  =>  (X - Y) * C
;; ---> together:   (X * C) <plusmin> (Y * C)  =>  (X <plusmin> Y) * C

;
;; (X - C1) + (Y - C2)  =>  (X + Y) - (C1 + C2)
;; (X - C1) - (Y - C2)  =>  (X - Y) - (C1 - C2)
;


        ; result should be:  29  42  40  87  75  35

        xx=6
        yy=8
        yy = (xx+5)+(yy+10)
        txt.print_ub(yy)        ; 29
        txt.nl()

        xx=6
        yy=8
        yy = (xx*3)+(yy*3)
        txt.print_ub(yy)        ; 42
        txt.nl()

        xx=13
        yy=5
        yy = (xx*5)-(yy*5)
        txt.print_ub(yy)        ; 40
        txt.nl()

        xx=100
        yy=8
        yy = (xx+5)-(yy+10)
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
