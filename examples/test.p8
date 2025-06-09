%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.print_bool(read_loadlist())
    }


    sub read_loadlist() -> bool {
        cx16.r0++
        if cx16.r0==0
            return false
        cx16.r1++
    }
}
