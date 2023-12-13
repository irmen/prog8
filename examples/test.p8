%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        ; expected output: 0000
        cx16.r0L = test_c_clear()
        cx16.r1L = test_z_clear()
        cx16.r2L = test_n_clear()
        cx16.r3L = test_v_clear()
        txt.print_ub(cx16.r0L)
        txt.print_ub(cx16.r1L)
        txt.print_ub(cx16.r2L)
        txt.print_ub(cx16.r3L)
        txt.nl()
        ; expected output: 1111
        cx16.r0L = test_c_set()
        cx16.r1L = test_z_set()
        cx16.r2L = test_n_set()
        cx16.r3L = test_v_set()
        txt.print_ub(cx16.r0L)
        txt.print_ub(cx16.r1L)
        txt.print_ub(cx16.r2L)
        txt.print_ub(cx16.r3L)
        txt.nl()

        ; exptected output: no2, no3,  no5, yes6, no7, yes8
        if not test_c_clear()
            goto skip1
        else
            txt.print("no1\n")

skip1:
        if test_c_clear()
            goto skip2
        else
            txt.print("no2\n")

skip2:

        if not test_c_set()
            goto skip3
        else
            txt.print("no3\n")

skip3:
        if test_c_set()
            goto skip4
        else
            txt.print("no4\n")

skip4:

        if test_c_clear() {
            txt.print("yes5\n")
            goto skip5
        }
        txt.print("no5\n")

skip5:
        if not test_c_clear() {
            txt.print("yes6\n")
            goto skip6
        }
        txt.print("no6\n")

skip6:
        if test_c_clear()
            txt.print("yes7\n")
        else
            txt.print("no7\n")

        if not test_c_clear()
            txt.print("yes8\n")
        else
            txt.print("no8\n")


        while test_c_clear() {
            cx16.r0++
        }

        do {
            cx16.r0++
        } until test_c_set()
    }

    asmsub test_c_clear() -> bool @Pc {
        %asm {{
            clc
            rts
        }}
    }

    asmsub test_z_clear() -> bool @Pz {
        %asm {{
            lda  #1
            rts
        }}
    }

    asmsub test_n_clear() -> bool @Pn {
        %asm {{
            lda  #1
            rts
        }}
    }

    asmsub test_v_clear() -> bool @Pv {
        %asm {{
            clv
            rts
        }}
    }

    asmsub test_c_set() -> bool @Pc {
        %asm {{
            sec
            rts
        }}
    }

    asmsub test_z_set() -> bool @Pz {
        %asm {{
            lda  #0
            rts
        }}
    }

    asmsub test_n_set() -> bool @Pn {
        %asm {{
            lda  #-1
            rts
        }}
    }

    asmsub test_v_set() -> bool @Pv {
        %asm {{
            bit  +
+           rts
        }}
    }
}
