%import textio
%import diskio

%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        cx16.r2 = diskio.get_loadaddress("test.prg")
        txt.print_uwhex(cx16.r2, true)
    }
}
