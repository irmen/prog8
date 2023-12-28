%import textio
%zeropage basicsafe

; Note: this program can be compiled for multiple target systems.

main {

    sub start() {

        txt.lowercase()

        str s1 = "HELLO hello 1234 @[/]"        ; regular strings have default encoding (petscii on c64)
        str s2 = sc:"HELLO hello 1234 @[/]"     ; alternative encoding (screencodes on c64)

        txt.print("\n\n\n\nString output via print:\n")
        txt.print("petscii-str: ")
        txt.print(s1)

        txt.print("\n\nThe top two screen lines are set via screencodes.\n")
        ubyte i
        for i in 0 to len(s1)-1
            txt.setchr(i, 0, s1[i])

        for i in 0 to len(s2)-1
            txt.setchr(i, 1, s2[i])

        const ubyte c1 = 'z'
        const ubyte c2 = sc:'z'

        txt.print("\npetscii z=")
        txt.print_ub(c1)
        txt.print("\nscreencode z=")
        txt.print_ub(c2)
        txt.print("\n")
    }
}
