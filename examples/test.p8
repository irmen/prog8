%zeropage basicsafe
%option no_sysinit

main {
    &ubyte io_reg = $d021
    ubyte @shared result
    sub start() {
        result = io_reg
        result = io_reg
        result = io_reg
        io_reg = 0
        io_reg = 0
        io_reg = 0
    }
}
