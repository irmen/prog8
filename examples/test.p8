%import textio
%encoding iso
main {
    sub start() {
        long @shared w

        for w in "derp" {
            txt.chrout(w)
        }

;        for w in [1111,2222,3333] {
;            txt.print_w(w)
;            txt.nl()
;        }
    }
}
