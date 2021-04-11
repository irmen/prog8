%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte yy
        ubyte joy=1
        ubyte zz
        str foobar="foobar"
        uword joyw=1

        if joyw + 1000 > 1000
            txt.print(">1000")
        if joyw + 1000 > 1011
            txt.print(">1011")
        if joyw + 1000 > & foobar
            txt.print(">&foobar")
        if joyw + 1000 > joyw
            txt.print(">&foobar")

        if joy + 10 > 10
            txt.print(">10")
        if joy + 10 >11
            txt.print(">11")

        joy >>= 1
        if_cs
            yy++

        joy >>= 1
        if_cs
            yy++

; TODO the shifting checks above result in way smaller code than this:
         if joy & %00000001
            yy++
         if joy & %00000010
            yy++

    }
}

