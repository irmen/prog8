%import c64lib
%import c64textio
%zeropage basicsafe


main {

    sub start() {

        txt.lowercase()

        str s1 = "HELLO hello 1234 @[/]"      ; regular strings have default encoding (petscii on c64)
        str s2 = @"HELLO hello 1234 @[/]"     ; alternative encoding (screencodes on c64)

        txt.print("\n\n\n\nString output via print:\n")
        txt.print("petscii-str: ")
        txt.print(s1)
        txt.print("\nscrcode-str: ")
        txt.print(s2)

        txt.print("\n\nThe top two screen lines are set via screencodes.\n")
        ubyte i
        for i in 0 to len(s1)-1
            @($0400+i) = s1[i]

        for i in 0 to len(s2)-1
            @($0400+40+i) = s2[i]

        ubyte c1 = 'z'
        ubyte c2 = @'z'

        txt.print("\npetscii z=")
        txt.print_ub(c1)
        txt.print("\nscreencode z=")
        txt.print_ub(c2)
        txt.print("\n")
    }
}
