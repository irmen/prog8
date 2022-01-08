%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        1.234 |> addfloat |> addfloat |> floats.print_f
        txt.nl()
          9 * 3 |> assemblything
            |> sin8u
            |> add_one    |> times_two
            |> txt.print_uw
    }

    sub addfloat(float fl) -> float {
        return fl+1.11
    }
    sub add_one(ubyte input) -> ubyte {
        return input+1
    }

    sub times_two(ubyte input) -> uword {
        return input*$0002
    }

    asmsub assemblything(ubyte input @A) -> ubyte @A {
        %asm {{
            asl a
            rts
        }}
    }
}


;main {
;    sub start() {
;        %asm {{
;            lda  #<float5_111
;            ldy  #>float5_111
;            jsr  floats.MOVFM
;            lda  #<float5_122
;            ldy  #>float5_122
;            jsr  floats.FADD
;            jsr  floats.FOUT
;            sta  $7e
;            sty  $7f
;            ldy  #0
;_loop
;            lda  ($7e),y
;            beq  _done
;            jsr  c64.CHROUT
;            iny
;            bne  _loop
;_done
;            rts
;
;float5_111	.byte  $81, $0e, $14, $7a, $e1  ; float 1.11
;float5_122	.byte  $81, $1c, $28, $f5, $c2  ; float 1.22
;
;        }}
;    }
;
;}
