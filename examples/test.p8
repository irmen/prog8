%zeropage dontuse

; NOTE: meant to test to virtual machine output target (use -target vitual)

main {
    sub start() {
        ubyte @shared xx = 11
        uword addr = $5000
        @(addr) = @(addr) + xx
        xx = xx ^ 44
        xx = xx | 44
        xx = xx & 44
    }
}
