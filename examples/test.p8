%import c64utils
%option enable_floats

~ main {

    sub start() {

        ubyte @zp ub = 22
        byte  @zp b = 22
        word @zp w = 2222
        uword @zp uw = 2222


        byte nonzp1 = 42
        byte nonzp2 = 42
        byte nonzp3 = 42
        foo.bar()
    }

}

~ foo {

sub bar() {
        ubyte @zp ub = 33
        byte  @zp b = 33
        word @zp w = 3333
        uword @zp uw = 3333

    word nonzp1 = 4444
    word nonzp2 = 4444
    word nonzp3 = 4444
    A=55
}
}
