%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

main {

    sub start() {

        ubyte x=3
        ubyte y=2
        ubyte a=1
        ubyte s=0
        float repr=4.4
        ;memset(c64.Screen, 40, 1)
        ;memset(c64.Screen+40, 80, 2)
        A=x
        A=y
        Y=a
        A=s
    }

    sub foo() {

    x:
    s:
    y:
    a:
        A=3

    }
}
