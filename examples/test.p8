%import textio
%zeropage basicsafe

main {

    sub start() {
        txt.print("\n"*25)

        word xx
        word compare

        xx=10
        if xx>9
            txt.print("1ok\n")
        else
            txt.print("1fault\n")

        if xx>10
            txt.print("2fault\n")
        else
            txt.print("2ok\n")

        if xx>11
            txt.print("3fault\n")
        else
            txt.print("3ok\n")

        if xx>2222
            txt.print("4fault\n")
        else
            txt.print("4ok\n")

        if xx>-9
            txt.print("5ok\n")
        else
            txt.print("5fault\n")

        if xx>-9999
            txt.print("6ok\n")
        else
            txt.print("6fault\n")

        if xx>0
            txt.print("7ok\n")
        else
            txt.print("7fault\n")

        xx=0
        if xx>0
            txt.print("8false\n")
        else
            txt.print("8ok\n")

        xx=-9999
        if xx>0
            txt.print("9false\n")
        else
            txt.print("9ok\n")

        txt.nl()

        xx=10
        compare=9
        if xx>compare
            txt.print("1ok\n")
        else
            txt.print("1fault\n")

        compare=10
        if xx>compare
            txt.print("2fault\n")
        else
            txt.print("2ok\n")

        compare=11
        if xx>compare
            txt.print("3fault\n")
        else
            txt.print("3ok\n")

        compare=2222
        if xx>compare
            txt.print("4fault\n")
        else
            txt.print("4ok\n")

        compare=-9
        if xx>compare
            txt.print("5ok\n")
        else
            txt.print("5fault\n")

        compare=-9999
        if xx>compare
            txt.print("6ok\n")
        else
            txt.print("6fault\n")

        compare=0
        if xx>compare
            txt.print("7ok\n")
        else
            txt.print("7fault\n")

        xx=0
        if xx>compare
            txt.print("8false\n")
        else
            txt.print("8ok\n")

        xx=-9999
        if xx>compare
            txt.print("9false\n")
        else
            txt.print("9ok\n")



        txt.nl()

        xx=9
        compare=9
        if xx+1>compare
            txt.print("1ok\n")
        else
            txt.print("1fault\n")

        compare=10
        if xx+1>compare
            txt.print("2fault\n")
        else
            txt.print("2ok\n")

        compare=11
        if xx+1>compare
            txt.print("3fault\n")
        else
            txt.print("3ok\n")

        compare=2222
        if xx+1>compare
            txt.print("4fault\n")
        else
            txt.print("4ok\n")

        compare=-9
        if xx+1>compare
            txt.print("5ok\n")
        else
            txt.print("5fault\n")

        compare=-9999
        if xx+1>compare
            txt.print("6ok\n")
        else
            txt.print("6fault\n")

        compare=0
        if xx+1>compare
            txt.print("7ok\n")
        else
            txt.print("7fault\n")

        xx=1
        if xx-1>compare
            txt.print("8false\n")
        else
            txt.print("8ok\n")

        xx=-9999
        if xx-1>compare
            txt.print("9false\n")
        else
            txt.print("9ok\n")

    }
}
