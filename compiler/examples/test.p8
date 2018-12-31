%import c64utils

~ main {

    sub start()  {
        ubyte i=0
        A= @($d020)
        A= @($d020+i)
        @($d020) = 0
        @($d020+i) = 0
        @($d020+i) = 1
        @($d020+i) = 2
        @($d020) = @($d020+i) + 1
        @($d020+i) = @($d020+i) + 1
        c64scr.print_ub(X)
    }
}
