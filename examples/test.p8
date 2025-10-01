%import textio
%zeropage basicsafe

main  {
    sub start() {
        cx16.r0 = 200
        cx16.r1 = 0
        plot_particles()
        txt.print_uw(cx16.r1)
        txt.print(" expected 0\n")

        cx16.r0 = 500
        cx16.r1 = 0
        plot_particles()
        txt.print_uw(cx16.r1)
        txt.print(" expected 1\n")

        cx16.r0 = 1
        cx16.r1 = 0
        plot_particles()
        txt.print_uw(cx16.r1)
        txt.print(" expected 1\n")
    }

    sub plot_particles() {
        if cx16.r0<10 or cx16.r0>319 {
            cx16.r1++
        }
    }
}
