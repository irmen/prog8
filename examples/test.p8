%option no_sysinit
%zeropage basicsafe
%import textio

main {
    sub start() {
        ^^word ptr

        if ptr!=0
            cx16.r0++
        if ptr==0
            cx16.r0++

        cx16.r0 = if ptr!=0 0 else ptr
        cx16.r1 = if ptr==0 0 else ptr
        cx16.r2 = if ptr!=0 ptr else 0
        cx16.r3 = if ptr==0 ptr else 0
    }
}
