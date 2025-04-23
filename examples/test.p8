%import textio
%zeropage dontuse
%option no_sysinit

main {
    ubyte @shared ub_global
    uword @shared uw_global = 0
    bool[4] @shared bool_array_global

    sub start() {
        ubyte @shared ub_scoped
        uword @shared uw_scoped = 0
        bool[4] @shared bool_array_scoped

        dump()

        ub_scoped++
        uw_scoped++
        bool_array_scoped[2]=true
        ub_global++
        uw_global++
        bool_array_global[2]=true

        dump()

        sub dump() {
            txt.print_ub(ub_global)
            txt.print_uw(uw_global)
            txt.print_bool(bool_array_global[2])
            txt.nl()
            txt.print_ub(ub_scoped)
            txt.print_uw(uw_scoped)
            txt.print_bool(bool_array_scoped[2])
            txt.nl()
        }
    }
}
