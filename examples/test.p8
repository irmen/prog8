%import textio
%zeropage dontuse

main {
    sub start() {
        ubyte num_files = 10

        while num_files {
            txt.print_ub(num_files)
            txt.nl()
            num_files--
        }
    }

    sub start2() {
        txt.print("\n"*25)

        word xx
        word compare

        xx=10
        if xx<9
            txt.print("1fault\n")
        else
            txt.print("1ok\n")

        if xx<10
            txt.print("2fault\n")
        else
            txt.print("2ok\n")

        if xx<11
            txt.print("3ok\n")
        else
            txt.print("3fault\n")

        if xx<2222
            txt.print("4ok\n")
        else
            txt.print("4fault\n")

        if xx<-9
            txt.print("5fault\n")
        else
            txt.print("5ok\n")

        if xx<-9999
            txt.print("6fault\n")
        else
            txt.print("6ok\n")

        if xx<0
            txt.print("7fault\n")
        else
            txt.print("7ok\n")

        xx=0
        if xx<0
            txt.print("8false\n")
        else
            txt.print("8ok\n")

        xx=-9999
        if xx<0
            txt.print("9ok\n")
        else
            txt.print("9fault\n")

        txt.nl()

        xx=10
        compare=9
        if xx<compare
            txt.print("1fault\n")
        else
            txt.print("1ok\n")

        compare=10
        if xx<compare
            txt.print("2fault\n")
        else
            txt.print("2ok\n")

        compare=11
        if xx<compare
            txt.print("3ok\n")
        else
            txt.print("3fault\n")

        compare=2222
        if xx<compare
            txt.print("4ok\n")
        else
            txt.print("4fault\n")

        compare=-9
        if xx<compare
            txt.print("5fault\n")
        else
            txt.print("5ok\n")

        compare=-9999
        if xx<compare
            txt.print("6fault\n")
        else
            txt.print("6ok\n")

        compare=0
        if xx<compare
            txt.print("7fault\n")
        else
            txt.print("7ok\n")

        xx=0
        if xx<compare
            txt.print("8fault\n")
        else
            txt.print("8ok\n")

        xx=-9999
        if xx<compare
            txt.print("9ok\n")
        else
            txt.print("9fault\n")


        txt.nl()

        xx=10
        compare=8
        if xx<compare+1
            txt.print("1fault\n")
        else
            txt.print("1ok\n")

        compare=9
        if xx<compare+1
            txt.print("2fault\n")
        else
            txt.print("2ok\n")

        compare=10
        if xx<compare+1
            txt.print("3ok\n")
        else
            txt.print("3fault\n")

        compare=2222
        if xx<compare+1
            txt.print("4ok\n")
        else
            txt.print("4fault\n")

        compare=-8
        if xx<compare-1
            txt.print("5fault\n")
        else
            txt.print("5ok\n")

        compare=-9999
        if xx<compare-1
            txt.print("6fault\n")
        else
            txt.print("6ok\n")

        compare=1
        if xx<compare-1
            txt.print("7fault\n")
        else
            txt.print("7ok\n")

        xx=0
        if xx<compare-1
            txt.print("8fault\n")
        else
            txt.print("8ok\n")

        xx=-9999
        if xx<compare-1
            txt.print("9ok\n")
        else
            txt.print("9fault\n")


    }
}
