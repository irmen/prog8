%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        str name = "irmen????????????"
        name[5] = 0

        txt.print(name)
        txt.nl()
        ubyte length = string.append(name, ".prg")

        txt.print_ub(length)
        txt.chrout('[')
        for cx16.r0L in 0 to length-1 {
            txt.chrout(name[cx16.r0L])
        }
        txt.chrout(']')
    }
}
