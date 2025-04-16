%import textio
%import strings
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name = "irmen de jong"
        str shortname = "i"
        str emptyname = ""

        ubyte idx
        bool found
        idx, found = strings.rfind(name, 'j')
        txt.print_bool(found)
        txt.print_ub(idx)
        txt.nl()
        idx, found = strings.rfind(name, 'x')
        txt.print_bool(found)
        txt.print_ub(idx)
        txt.nl()
        txt.nl()
        idx, found = strings.rfind(shortname, 'i')
        txt.print_bool(found)
        txt.print_ub(idx)
        txt.nl()
        idx, found = strings.rfind(shortname, 'x')
        txt.print_bool(found)
        txt.print_ub(idx)
        txt.nl()
        txt.nl()
        idx, found = strings.rfind(emptyname, 'i')
        txt.print_bool(found)
        txt.print_ub(idx)
        txt.nl()
        idx, found = strings.rfind(emptyname, 'x')
        txt.print_bool(found)
        txt.print_ub(idx)
        txt.nl()
    }
}
