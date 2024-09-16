%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ; nothing!
    }
}


derp {
    asmsub f_tell() -> uword @R0, uword @R1, uword @R2, uword @R3 {
        %asm {{
            jmp  p8s_internal_f_tell
        }}
    }

    sub internal_f_tell() {
        cx16.r1 = read4hex()

        sub read4hex() -> uword {
            str @shared hex = "0000000000000000000000000000000000000000000"
            cx16.r0++
            return cx16.r0
        }
    }
}
