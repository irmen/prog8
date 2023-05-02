%import textio
%import floats
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte from = 20
        ubyte target = 10

        ubyte xx
        for xx in from to target {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.print("done\n\n")
        for xx in target downto from {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.print("done\n\n")

        byte sfrom = -10
        byte starget = -20

        byte sxx
        for sxx in sfrom to starget {
            txt.print_b(sxx)
            txt.spc()
        }
        txt.print("done\n\n")
        for sxx in starget downto sfrom {
            txt.print_b(sxx)
            txt.spc()
        }
        txt.print("done\n\n")

        uword wfrom = 1020
        uword wtarget = 1010

        uword ww
        for ww in wfrom to wtarget {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.print("done\n\n")
        for ww in wtarget downto wfrom {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.print("done\n\n")

        word swfrom = -1010
        word swtarget = -1020
        word sww
        for sww in swfrom to swtarget {
            txt.print_w(sww)
            txt.spc()
        }
        txt.print("done\n\n")
        for sww in swtarget downto swfrom {
            txt.print_w(sww)
            txt.spc()
        }
        txt.print("done\n\n")

        ;  all of the above with stepsize 2 / -2
        for xx in from to target step 2{
            txt.print_ub(xx)
            txt.spc()
        }
        txt.print("done\n\n")
        for xx in target downto from step -2 {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.print("done\n\n")
        for sxx in sfrom to starget step 2 {
            txt.print_b(sxx)
            txt.spc()
        }
        txt.print("done\n\n")
        for sxx in starget downto sfrom step -2 {
            txt.print_b(sxx)
            txt.spc()
        }
        txt.print("done\n\n")
        for ww in wfrom to wtarget step 2 {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.print("done\n\n")
        for ww in wtarget downto wfrom step -2 {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.print("done\n\n")
        for sww in swfrom to swtarget step 2 {
            txt.print_w(sww)
            txt.spc()
        }
        txt.print("done\n\n")
        for sww in swtarget downto swfrom step -2 {
            txt.print_w(sww)
            txt.spc()
        }
        txt.print("done\n\n")

    }
}

