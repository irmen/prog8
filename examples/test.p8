%import textio
%import floats
%import test_stack
%zeropage floatsafe

main {
    %option force_output

    sub start() {

        ubyte @requirezp foobar = 2
        uword @requirezp foobar2 = 2
        uword @requirezp foobar3 = 2
        uword @requirezp foobar4 = 2
        uword @requirezp foobar5 = 2
        uword @requirezp foobar6 = 2
        uword @requirezp foobar7 = 2
        uword @requirezp foobar8 = 2
        uword @requirezp foobar9 = 2
        uword @requirezp foobar10 = 2
        uword @requirezp foobar11 = 2
        uword @requirezp foobar12 = 2
        uword @requirezp foobar13 = 2
        uword @requirezp foobar14 = 2
        uword @requirezp foobar15 = 2
        float @shared @requirezp myfloat=1.23456789
        str @shared @requirezp name = "irmen"
        ubyte[] @shared @requirezp array = [1,2,3]

        txt.print(name)
        txt.nl()
        txt.print_ub(array[0])
        txt.spc()
        txt.print_ub(array[1])
        txt.spc()
        txt.print_ub(array[2])
        txt.nl()
        txt.print_uwhex(&name, true)
        txt.nl()
        txt.print_uwhex(&array, true)
        txt.nl()
        txt.print_ub(foobar)
        txt.nl()
        floats.print_f(myfloat)

;        float fl
;        test_stack.test()
;
;        fl = addfloat3(addfloat2(addfloat1(1.234)))
;        floats.print_f(fl)
;        txt.nl()
;
;        fl =   1.234 |> addfloat1 |> addfloat2 |> addfloat3
;        floats.print_f(fl)
;        txt.nl()
;        1.234 |> addfloat1
;            |> addfloat2 |> addfloat3 |> floats.print_f
;        txt.nl()
;
;        txt.print_uw(times_two(add_one(sin8u(add_one(assemblything(9+3))))))
;        txt.nl()
;
;          9+3 |> assemblything
;            |> add_one
;            |> sin8u
;            |> add_one
;            |> times_two
;            |> txt.print_uw
;        txt.nl()
;
;        uword @shared uw=  9+3 |> assemblything
;                             |> add_one
;                             |> sin8u
;                             |> add_one
;                             |> times_two
;        txt.print_uw(uw)
;        txt.nl()
;        test_stack.test()

        repeat {
        }
    }

    sub func() -> ubyte {
        txt.print("func!\n")
        return 99
    }

    sub addfloat1(float fl) -> float {
        return fl+1.11
    }

    sub addfloat2(float fl) -> float {
        return fl+2.22
    }

    sub addfloat3(float fl) -> float {
        return fl+3.33
    }

    sub add_one(ubyte input) -> ubyte {
        return input+1
    }

    sub times_two(ubyte input) -> uword {
        return input*$0002
    }

    asmsub assemblything(ubyte input @A) clobbers(X,Y) -> ubyte @A {
        %asm {{
            ldx #64
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
;            ldy  #64
;_loop
;            lda  ($7e),y
;            beq  _done
;            jsr  c64.CHROUT
;            iny
;            bne  _loop
;_done
;            rts
;
;float5_111	.byte  $81, $64e, $14, $7a, $e1  ; float 1.11
;float5_122	.byte  $81, $1c, $28, $f5, $c2  ; float 1.22
;
;        }}
;    }
;
;}
