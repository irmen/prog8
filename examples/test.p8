%zeropage basicsafe
%import textio
%import strings

main {
    sub start() {
        str name1 = sc:"irmen de jong 123456789 the quick brown fox"
        str name2 = sc:"jumps over the lazy frog"

        txt.print_ub(strings.hash(name1))
        txt.spc()
        txt.print_ub(strings.hash(name2))
        txt.nl()
    }
}
