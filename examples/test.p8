%import textio
%zeropage basicsafe

main {
    sub start() {
        ; TODO other variants of this const folding
        uword load_location = $6000
        word llw = $6000

        cx16.r0 = load_location + 9000 + 1000 + 1000
        cx16.r1L = @(load_location + 8000 + 1000 + 1000)
        cx16.r2 = 8000 + 1000 + 1000 + load_location
        cx16.r3L = @(8000 + 1000 + 1000 + load_location)

        cx16.r0s = llw - 900 + 999
        cx16.r1L = @(llw - 900 + 999)
        cx16.r0s = llw - 900 + 999
        cx16.r1L = @(llw - 9000 + 999)

        cx16.r0s = llw - 900 - 999
        cx16.r1L = @(llw - 900 - 999)
        cx16.r0s = llw - 900 - 999
        cx16.r1L = @(llw - 9000 - 999)

    }
}
