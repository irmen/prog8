%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        bool @shared statusc = test_carry_set()
        bool @shared statusv = test_v_set()
        bool @shared statusz = test_z_set()
        bool @shared statusn = test_n_set()

        if test_carry_set() {
            txt.print("set!\n")
        }
        if test_v_set() {
            txt.print("set!\n")
        }
        if test_z_set() {
            txt.print("set!\n")
        }
        if test_n_set() {
            txt.print("set!\n")
        }
    }

    asmsub test_carry_set() -> bool @Pc {
        %asm {{
            sec
            rts
        }}
    }

    asmsub test_v_set() -> bool @Pv {
        %asm {{
            sec
            rts
        }}
    }

    asmsub test_z_set() -> bool @Pz {
        %asm {{
            sec
            rts
        }}
    }

    asmsub test_n_set() -> bool @Pn {
        %asm {{
            sec
            rts
        }}
    }
}
