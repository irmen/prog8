%import textio
%import floats
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte x = testdefer()
        txt.print("result from call=")
        txt.print_ub(x)
        txt.nl()
        float f = testdeferf()
        txt.print("result from fcall=")
        floats.print(f)
        txt.nl()

        floats.push(f)
        txt.print("pushed f")
        f = floats.pop()
        floats.print(f)
        txt.nl()
    }

    sub testdeferf() -> float {
        defer {
            txt.print("defer in floats\n")
        }
        float @shared zz = 111.111
        cx16.r0++
        return 123.456 + zz
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
            return var + other()
        }
        else {
            var++
            txt.print("var=")
            txt.print_ub(var)
            txt.nl()
            return 255
        }


    }

    sub other() -> ubyte {
        txt.print("other()\n")
        return 11
    }
}
