%import c64lib
%import c64utils
%zeropage basicsafe


main {

    sub start() {

        c64.VMCSB |= 2  ; switch to lowercase charset

        str s1 = "HELLO hello 1234 @[/]\n"
        str s2 = c64scr("HELLO hello 1234 @[/]\n")

        c64scr.print("\n\n\n\nString output via print:\n")
        c64scr.print(s1)
        c64scr.print(s2)

        c64scr.print("\nThe top two screen lines are set via screencodes.\n")
        ubyte i
        for i in 0 to len(s1)-1
            @($0400+i) = s1[i]

        for i in 0 to len(s2)-1
            @($0400+40+i) = s2[i]
    }
}
