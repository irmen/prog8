%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte @shared n=20
        ubyte @shared x=10

        if n < x {
          ; nothing here, conditional gets inverted
        } else {
            cx16.r0++
        }
        cx16.r0L = n<x == 0
        cx16.r1L = not n<x
    }
}
