%import textio
%zeropage basicsafe

; Demonstrates the "xx += 2 -> xx++; xx++" AST rewrite in StatementOptimizer.
; On 6502 two INC/DEC are cheaper than a load/add/store of a constant 2, so
; the optimizer splits the add into two increments. On m68k a single addq #2
; is one instruction, so the split is strictly worse there.

main {
    sub start() {
        incdec()
        quick()
        more()
    }

    sub incdec() {
        ubyte @shared x = 5
        x += 2      ; expect 7
        txt.print("x=")
        txt.print_ub(x)
        txt.nl()

        ubyte @shared y = 20
        y -= 2      ; expect 18
        txt.print("y=")
        txt.print_ub(y)
        txt.nl()
    }

    sub quick() {
        ubyte @shared p = 5
        p += 7      ; expect 12
        txt.print("p=")
        txt.print_ub(p)
        txt.nl()

        ubyte @shared q = 20
        q -= 7      ; expect 13
        txt.print("q=")
        txt.print_ub(q)
        txt.nl()
    }

    sub more() {
        ubyte @shared a = 11
        a += 55      ; expect 66
        txt.print("a=")
        txt.print_ub(a)
        txt.nl()

        ubyte @shared b = 100
        b -= 70      ; expect 30
        txt.print("b=")
        txt.print_ub(b)
        txt.nl()
    }
}
