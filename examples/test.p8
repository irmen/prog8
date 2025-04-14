%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword @shared crc1 = $ED
        uword @shared temp = $100

        crc1 = temp - crc1
    }
}
