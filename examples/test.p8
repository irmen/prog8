%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        cx16.r0L=0
        if cx16.r0L==0 {
            uword[] addresses = [scores2, start]
            uword[] @split scores1 = [10, 25, 50, 100]      ; can never clear more than 4 lines at once
            uword[] @split scores2 = [10, 25, 50, 100]      ; can never clear more than 4 lines at once

            cx16.r0 = &scores1
            cx16.r1 = &scores2
            cx16.r2 = &addresses
        }
    }

}
