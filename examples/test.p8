%import floats
%import textio
%zeropage basicsafe

main {
    sub start() {
        cbm.SETTIM(0,0,0)
        float xx = 1.234
        floats.print(xx)
        txt.nl()
        xx= floats.time()
        floats.print(xx)
        txt.nl()
        floats.print(floats.time())
        txt.nl()

        txt.print("waiting 333 jiffies... ")
        sys.wait(333)
        floats.print(floats.time())
        txt.nl()
    }
}
