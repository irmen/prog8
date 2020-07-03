%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats


; TODO: fix register argument clobbering when calling asmsubs.
; for instance if the first arg goes into Y, and the second in A,
; but when calculating the second argument clobbers Y, the first argument gets destroyed.

main {

    sub start() {
        function(20, calculate())
        asmfunction(20, calculate())

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

    sub calculate() -> ubyte {
        Y = 99
        return Y
    }
}


