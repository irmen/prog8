%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        const ubyte CVALUE = 123
        const long CLONG = 555555
        ubyte @shared vvalue = 99

        cx16.r0L = CVALUE + 100
        cx16.r1L = vvalue + 100
    }
}
