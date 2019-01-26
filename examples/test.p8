%import c64lib
%import c64utils


~ main {

    sub start() {

        uword num_hours=2
        uword num_minutes=10
        uword num_seconds=14

        uword total =     num_hours * 3600 + num_minutes * 60 + num_seconds

        uword total2 =     num_hours * 3600
                            + num_minutes * 60
                            + num_seconds

        c64scr.print_uw(total)
        c64.CHROUT('\n')
        c64scr.print_uw(total2)
        c64.CHROUT('\n')

    }

}
