%import textio
%import strings
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        txt.iso()

        str name = iso:"Irmen De Jong"
        str name2 = iso:"Irmen De Jong"
        str name3 = iso:"Irmen De Jong"
        str name4 = iso:"Irmen De Jong"

        txt.print(name)
        txt.nl()
        txt.nl()
        strings.lower_iso(name)
        strings.upper_iso(name2)
        txt.print(name)
        txt.nl()
        txt.print(name2)
        txt.nl()
        txt.nl()

        for cx16.r0L in 0 to len(name3)-1 {
            name3[cx16.r0L] = strings.lowerchar_iso(name3[cx16.r0L])
        }
        txt.print(name3)
        txt.nl()
        for cx16.r0L in 0 to len(name4)-1 {
            name4[cx16.r0L] = strings.upperchar_iso(name4[cx16.r0L])
        }
        txt.print(name4)
        txt.nl()
    }

}
