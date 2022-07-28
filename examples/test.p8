%import textio
%import string
%zeropage kernalsafe

main {
    sub start() {
        cx16.r0 = $ea31
        cx16.r15 = $ff99
        str name = "irmen"

        txt.print_uwhex(cx16.r0, true)
        txt.spc()
        txt.print_uwhex(cx16.r15, true)
        txt.nl()
        cx16.r7 = &name
        txt.chrout(cx16.r7[0])
        txt.chrout(cx16.r7[1])
        txt.chrout(cx16.r7[2])
        txt.chrout(cx16.r7[3])
        txt.chrout(cx16.r7[4])
        txt.nl()

        repeat {
        }
    }
}
