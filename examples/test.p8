%import textio
%import syslib
%zeropage basicsafe

main {


    sub start() {
        ubyte value
        ubyte bb1

        ; TODO why is this generating so much larger code:  (only with asmsub btw)
        value = cx16.vpeek(lsb(cx16.r0), mkword(value, bb1))
        value = cx16.vpeek(lsb(cx16.r0), mkword(value, bb1))

        ubyte lx = lsb(cx16.r0)
        value = cx16.vpeek(lx, mkword(value, bb1))
        value = cx16.vpeek(lx, mkword(value, bb1))
    }
}
