%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        if diskio.loadlib("lib-6000.bin", $6000) != 0 {
            thelibrary.init()
            thelibrary.message()
        }
    }
}

thelibrary {
    ; initialize the library, required as first call
    extsub $6000 = init() clobbers(A)

    ; the routine in the library
    extsub $6003 = message() clobbers(A)
}
