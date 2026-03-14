%import textio
%zeropage basicsafe

main {

    sub start() {
        sys.die(99, "something bad happened")
    }
}

;%import textio
;%zeropage basicsafe
;%option no_sysinit
;
;main {
;    sub start() {
;        routine()
;        routine()
;        check()
;    }
;
;    sub check() {
;        ubyte tmp_x
;        ubyte tmp_y
;        ubyte tmp_z
;        tmp_x = cx16.r0L
;        tmp_y = cx16.r1L
;        tmp_z = cx16.r2L
;        tmp_x++
;        tmp_y++
;        tmp_z++
;    }
;
;    sub routine() {
;        ubyte @nozp @shared local_nozp =44     ; set to 0 at subroutine entry (STZ) - redundant because of initialization below
;        ubyte @zp @shared local_zp, local_zp2, zp99          ; set to 0 at subroutine entry (STZ) - redundant because of initialization below
;        ubyte @zp @shared local_zp3         ; set to 0 at subroutine entry (STZ) - redundant because of initialization below
;
;        ; do some stuff that doesn't touch the variables
;        cx16.r0++
;        zp99++
;        repeat 99 cx16.r1++
;        swap(cx16.r0L, local_zp2)       ; ... except zp2 here - so that one should remain zeroed out initially
;        for cx16.r0L in 0 to 10 {
;            cx16.r1++
;        }
;
;
;        local_nozp = 11
;        local_zp = 22
;        local_zp2 = 33
;        local_zp3, void = multi()
;
;        txt.print_ub(local_nozp)
;        txt.spc()
;        txt.print_ub(local_zp)
;        txt.spc()
;        txt.print_ub(local_zp2)
;        txt.nl()
;
;        local_nozp++
;        local_zp++
;        local_zp2++
;
;        txt.print_ub(local_nozp)
;        txt.spc()
;        txt.print_ub(local_zp)
;        txt.spc()
;        txt.print_ub(local_zp2)
;        txt.spc()
;        txt.nl()
;    }
;
;    sub multi() -> ubyte, ubyte {
;        return cx16.r0L, cx16.r1L
;    }
;}
