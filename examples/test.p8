%import textio
%zeropage basicsafe

main {
    sub start() {
        ^^bool flags = 5000
        ^^word words = 6000
        ^^long longs = 7000

        cx16.r9 = 10
        @(5000 + 10) = 1
        @(6000 + 20) = 0
        @(6000 + 21) = 1
        @(7000 + 40) = 0
        @(7000 + 41) = 0
        @(7000 + 42) = 0
        @(7000 + 43) = 1

        if flags[cx16.r9] {
            flags[cx16.r9] = false
            txt.print("flags is set to ")
            txt.print_bool(flags[cx16.r9])
            txt.nl()
        } else txt.print("flags is false\n")

        if words[cx16.r9]!=0 {
            words[cx16.r9] = 1234          ; TODO fix cast error for negative numbers
            txt.print("words is set to ")
            txt.print_w(words[cx16.r9])
            txt.nl()
        } else txt.print("words is 0\n")


        if longs[cx16.r9]!=0 {
            longs[cx16.r9] = -999999
            txt.print("longs is set to ")
            txt.print_l(longs[cx16.r9])
            txt.nl()
        } else txt.print("longs is 0\n")
    }
}
