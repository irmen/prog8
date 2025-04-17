%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        &uword zeropagevar = $20
        uword @shared ptr = 999

        cx16.r0L = @(zeropagevar)
        cx16.r1L = @(ptr)
    }
}
