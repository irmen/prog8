%zeropage basicsafe
%option no_sysinit


main {
    ubyte[8] scratch
    ubyte counter

    sub start() {
        scratch[0] = 10
        scratch[1] = 20

        ; Pattern 1: PtSub 2-byte-param spill
        ; both args are array-indexed reads (non-simple) -> needAsaveForExpr true for both
        two_byte_sub(scratch[0], scratch[1])

        ; Pattern 2: Asmsub XY-A spill
        ; XY gets simple identifier, A gets value from a non-inlinable function call
        asm_xy_a(scratch, get_value(counter))

        ; Pattern 3: Asmsub Y-X spill
        ; Y gets number, X gets binary expression with non-inlinable function call
        asm_y_x(0, get_value(counter) + 5)

        ; Pattern 4: Asmsub Y-X-A spill
        ; Y gets number, X gets binary expression with non-inlinable function call
        ; (A gets simple number, no further spill)
        asm_y_x_a(0, get_value(counter) + 3, 42)

        ; Pattern 5: Library asmsub Y-X (txt.plot) spill
        ; Y gets simple number, X gets complex expression -> Y saved before eval of X
        txt.plot(0, get_value(counter) + 5)

        ; Pattern 6: Library asmsub X-Y-A (txt.setchr) spill
        ; Y (row) gets simple number, X (col) gets complex expression -> Y saved before eval of X
        ; (A gets simple number, no further spill)
        txt.setchr(get_value(counter), 0, 42)
    }

    sub two_byte_sub(ubyte a, ubyte b) {
        counter += a
        counter += b
    }

    sub get_value(ubyte x) -> ubyte {
        if x==0
            return 42
        return x
    }

    asmsub asm_xy_a(uword ptr @XY, ubyte val @A) clobbers(A) {
        %asm {{
            rts
        }}
    }

    asmsub asm_y_x(ubyte col @Y, ubyte row @X) {
        %asm {{
            rts
        }}
    }

    asmsub asm_y_x_a(ubyte col @Y, ubyte row @X, ubyte ch @A) clobbers(A) {
        %asm {{
            rts
        }}
    }
}

txt {
    asmsub plot(ubyte col @ Y, ubyte row @ X) {
        %asm {{
            clc
            jmp  $FFF0
        }}
    }

    asmsub setchr(ubyte col @X, ubyte row @Y, ubyte character @A) clobbers(A) {
        %asm {{
            rts
        }}
    }
}
