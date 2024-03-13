%import textio
%zeropage basicsafe
%option no_sysinit

main {
    bool @shared var1, var2
    bool[2] barray = [false, true]
    ubyte success

    sub start() {
        no_else()
    }

    sub no_else() {
        txt.print("bool no_else: ")
        success=0

        var1=true
        var2=false

        if var1!=var2
            txt.print("yes")

    }
}
