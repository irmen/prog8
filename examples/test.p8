%import textio
%import string
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        str name = "zn.iff.jpg"

        ubyte index
        bool found

        index, found = string.find(name, '.')
        if found {
            txt.print_ub(index)
            txt.nl()
        } else {
            txt.print(". not found\n")
        }

        index, found = string.find(name, '@')
        if found {
            txt.print_ub(index)
            txt.nl()
        } else {
            txt.print("@ not found\n")
        }

        index, found = string.rfind(name, '.')
        if found {
            txt.print_ub(index)
            txt.nl()
        } else {
            txt.print(". not r found\n")
        }

        index, found = string.rfind(name, '@')
        if found {
            txt.print_ub(index)
            txt.nl()
        } else {
            txt.print("@ not r found\n")
        }
    }
}
