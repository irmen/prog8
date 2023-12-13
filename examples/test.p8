%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        if test_c_set()
            txt.print("yes1\n")
        else
            goto skip1

        txt.print("no1\n")

skip1:
        if test_c_clear()
            txt.print("yes2\n")
        else
            goto skip2

        txt.print("no1\n")

skip2:
        txt.print("done\n")
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
