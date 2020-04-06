%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

foobar {
    %option force_output

    ubyte xx

    sub derp() {
        byte yy=cos8(A)

        if A==0 {
            ; ubyte qq=cos8(A)
            A=54
        }
    }

}

main {
    sub start() {
        float[] floats = [1.1, 2.2]
        ubyte index=1

        c64flt.print_f(floats[0])
        c64flt.print_f(floats[1])
        floats[0] = 9.99
        floats[index] = 8.88
        c64flt.print_f(floats[0])
        c64flt.print_f(floats[1])

        foobar.derp()
        when A {
        100 -> Y=4
        101 -> Y=5
        1 -> Y=66
        10 -> Y=77
        else -> Y=9
        }

        A+=99
    }
}


