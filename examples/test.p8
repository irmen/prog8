%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        cx16.r0=0

        if cx16.r0==0
            cx16.r1L=42
        else
            cx16.r1L=99

        cx16.r2L = if cx16.r0==0 42 else 99

        if cx16.r0==33
            cx16.r1L=42
        else
            cx16.r1L=99

        cx16.r2L = if cx16.r0==33 42 else 99

        if cx16.r0!=3333
            cx16.r1L=42
        else
            cx16.r1L=99

        cx16.r2L = if cx16.r0!=3333 42 else 99

        if cx16.r0!=0
            cx16.r1L=42
        else
            cx16.r1L=99

        cx16.r2L = if cx16.r0!=0 42 else 99

        if cx16.r0!=33
            cx16.r1L=42
        else
            cx16.r1L=99

        cx16.r2L = if cx16.r0!=0 42 else 99

        if cx16.r0==cx16.r9
            cx16.r1L=42
        else
            cx16.r1L=99

        cx16.r2L = if cx16.r0==cx16.r9 42 else 99

        if cx16.r0!=cx16.r9
            cx16.r1L=42
        else
            cx16.r1L=99

        cx16.r2L = if cx16.r0!=cx16.r9 42 else 99

        if cx16.r0>cx16.r1
            cx16.r1L=42
        else
            cx16.r1L=99

        cx16.r2L = if cx16.r0>cx16.r1 42 else 99
    }
}
