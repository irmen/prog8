%import textio
%option no_sysinit

main {
    sub start() {
        cx16.r0s = if cx16.r0L < cx16.r1L -1 else 1
    }
}
