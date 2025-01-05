%import textio
%option no_sysinit
%zeropage basicsafe

main {
    ubyte[4] attrs
    ubyte[4] object

    sub start() {
        cx16.r0 = mkword(attrs[cx16.r2L], object[cx16.r2L])
        cx16.r1 = mkword(attrs[cx16.r2L], 22)
        cx16.r2 = mkword(22,attrs[cx16.r2L])
        explode(1, attrs[2]+2)
        explode(attrs[2]+2, 1)
    }

    sub explode(ubyte a1, ubyte a2) -> uword {
        return mkword(attrs[cx16.r2L], object[cx16.r2L])
    }
}
