

%import textio
%zeropage basicsafe


main {
    sub start() {
        check(0)
        check(255)
        check(256)
        check($0f00)
        check(12345)

        txt.print("\n4096\n")
        check2(0)
        check2(4095)
        check2(4096)
        check2($2f00)
        check2(12345)
    }

    sub check(uword test) {
        if test>255
            txt.print("yes1 ")
        else
            txt.print("no1 ")

        if test>=256
            txt.print("yes2 ")
        else
            txt.print("no2 ")

        if msb(test)!=0
            txt.print("yes3 ")
        else
            txt.print("no3 ")

        txt.nl()
    }

    sub check2(uword test) {
        if test>4095
            txt.print("yes1 ")
        else
            txt.print("no1 ")

        if test>=4096
            txt.print("yes2 ")
        else
            txt.print("no2 ")

        if msb(test)>=$10
            txt.print("yes3 ")
        else
            txt.print("no3 ")

        txt.nl()
    }
}
