%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        &ubyte[256] foo= $c000
        ubyte[] array=[1,2,3]
        str string = "hello"

        string = 3
        array = 5
        foo = $c100
        c64scr.print_uwhex(foo, 1)

        foo[100]=10
    }
}

