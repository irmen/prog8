%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name = "irmen"
        ubyte @shared cc='m'

        cx16.r0=9999

        if cx16.r0<10000 and 'q' in name
            txt.print("yes")
    }
}
