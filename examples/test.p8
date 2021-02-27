%import textio
%zeropage basicsafe

main {

    sub start() {
        uword screen = 2000
        ubyte i = 1
        uword w = 33
        str derp  ="derp"
        ubyte[] array = [1,2,3]

        @(screen+i) = 128
        @(i+screen) = 129

        txt.print("done\n")
    }
}
