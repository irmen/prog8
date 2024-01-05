%import textio
%import string
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str s = "the quick brown fox jumps over the lazy dog."
        uword sp = &s

        cbm.SETTIM(0,0,0)
        repeat 5000 {
            cx16.r0L = 'v' in s
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        cbm.SETTIM(0,0,0)
        repeat 5000 {
            cx16.r0L = string.contains(s, 'v')
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

    }
}
