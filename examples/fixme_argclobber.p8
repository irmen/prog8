%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats


; TODO: optimize register arg value passing when there is no risk of register clobbering

main {

    sub start() {
        function(20, calculate())
        asmfunction(20, calculate())
        asmfunction2(1, 2)      ; TODO optimize
        ubyte arg = 3
        ubyte arg2 = 4
        asmfunction3(arg, arg2)     ; TODO optimize
        Y=5
        A=6
        asmfunction4(Y,A)       ; TODO optimize
        A=7
        Y=8
        asmfunction5(A,Y)       ; TODO cannot optimize, fix result

        c64.CHROUT('\n')

        if @($0400)==@($0402) and @($0401) == @($0403) {
            c64scr.print("ok: results are same\n")
        } else {
            c64scr.print("error: result differ; arg got clobbered\n")
        }
    }

    sub function(ubyte a1, ubyte a2) {
        ; non-asm function passes via stack, this is ok
        @($0400) = a1
        @($0401) = a2
    }

    asmsub asmfunction(ubyte a1 @ Y, ubyte a2 @ A) {
        ; asm-function passes via registers, risk of clobbering
        %asm {{
            sty  $0402
            sta  $0403
        }}
    }

    asmsub asmfunction2(ubyte a1 @ Y, ubyte a2 @ A) {
        ; asm-function passes via registers, risk of clobbering
        %asm {{
            sty  $0404
            sta  $0405
        }}
    }

    asmsub asmfunction3(ubyte a1 @ Y, ubyte a2 @ A) {
        ; asm-function passes via registers, risk of clobbering
        %asm {{
            sty  $0406
            sta  $0407
        }}
    }

    asmsub asmfunction4(ubyte a1 @ Y, ubyte a2 @ A) {
        ; asm-function passes via registers, risk of clobbering
        %asm {{
            sty  $0408
            sta  $0409
        }}
    }
    asmsub asmfunction5(ubyte a1 @ Y, ubyte a2 @ A) {
        ; asm-function passes via registers, risk of clobbering
        %asm {{
            sty  $040a
            sta  $040b
        }}
    }

    sub calculate() -> ubyte {
        Y = 99
        return Y
    }
}


