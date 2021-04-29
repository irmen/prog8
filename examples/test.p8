
main {

    sub start() {

        ubyte xx

        xx |= 44
        xx &= 44
        xx ^= 44

        xx |= @($d020)
        xx &= @($d020)
        xx ^= @($d020)

        uword ww

        ww |= $4433
        ww &= $4433
        ww ^= $4433
        ww |= @($d020)
        ww &= @($d020)
        ww ^= @($d020)
    }
}
