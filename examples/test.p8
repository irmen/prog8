%import textio
%zeropage basicsafe
%import bcd

main {
    sub start() {
        cbm.SETTIML($12fe56)
        repeat {
            txt.home()
            txt.print_ulhex(cbm.RDTIML(), false)
        }
    }
}
