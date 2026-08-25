%zeropage basicsafe
%encoding iso
%import textio

main
{
    struct Point {
        uword x
        ubyte y
    }

    sub print_point(^^Point p) {
        txt.chrout('(')
        txt.print_uw(p.x)
        txt.chrout(',')
        txt.print_ub(p.y)
        txt.chrout(')')
    }

    sub start() {
        txt.iso()
        pointer buffer = memory("buffer", sizeof(Point), 1)
        print_point(buffer)
        (buffer as ^^Point).x = 160
        (buffer as ^^Point).y = 120
        print_point(buffer)         ; expected: (160,120)

        ; other ways to trigger the same codegen path: constant byte store via pointer+const
        ; (6502 targets only, uword indexing is not supported on the virtual target)
        uword @shared ptr_zp = memory("buf2", 8, 1)
        uword @shared @nozp ptr_nozp = memory("buf3", 8, 1)
        ptr_zp[3] = $80
        @(ptr_zp+4) = $27
        ptr_nozp[5] = $42
        @(ptr_nozp+6) = $99
        txt.nl()
        ; expected: 128 39 66 153
        ; (with the bug, these printed the low byte of the buffer address instead)
        txt.print_ub(ptr_zp[3])     ; $80 = 128
        txt.spc()
        txt.print_ub(@(ptr_zp+4))   ; $27 = 39
        txt.spc()
        txt.print_ub(ptr_nozp[5])   ; $42 = 66
        txt.spc()
        txt.print_ub(@(ptr_nozp+6)) ; $99 = 153

        ;sys.poweroff_system()
    }

}
