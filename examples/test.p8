%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte x = testdefer()
        txt.print("result from call=")
        txt.print_ub(x)
        txt.nl()
    }

    sub testdefer() -> ubyte {
        ubyte var = 22

        defer txt.print("defer1\n")
        defer {
            txt.print("defer2, var=")
            txt.print_ub(var)
            txt.nl()
        }

        if var==22 {
            var = 88
            return var
        }
        else {
            var++
            txt.print("var=")
            txt.print_ub(var)
            txt.nl()
            return 255
        }


    }

    sub other() {
        cx16.r0++
    }
}
