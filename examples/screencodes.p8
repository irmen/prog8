%import c64lib
%import c64utils
%zeropage basicsafe


main {

    sub start() {

        c64.VMCSB |= 2  ; switch to lowercase charset

        str s1 = "HELLO hello 1234 @[/]"      ; regular strings have default encoding (petscii on c64)
        str s2 = @"HELLO hello 1234 @[/]"     ; alternative encoding (screencodes on c64)

        c64scr.print("\n\n\n\nString output via print:\n")
        c64scr.print("petscii-str: ")
        c64scr.print(s1)
        c64scr.print("\nscrcode-str: ")
        c64scr.print(s2)

        c64scr.print("\n\nThe top two screen lines are set via screencodes.\n")
        ubyte i
        for i in 0 to len(s1)-1
            @($0400+i) = s1[i]

        for i in 0 to len(s2)-1
            @($0400+40+i) = s2[i]

        ubyte c1 = 'z'
        ubyte c2 = @'z'

        c64scr.print("\npetscii z=")
        c64scr.print_ub(c1)
        c64scr.print("\nscreencode z=")
        c64scr.print_ub(c2)
        c64scr.print("\n")
    }
}
