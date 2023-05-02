%import textio
%import floats
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {

        ubyte target = 10
        ubyte from = 250

        ubyte xx
        for xx in from to target {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.print("done\n")
        for xx in target downto from {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.print("done\n")

        byte starget = -120
        byte sfrom = 120

        byte sxx
        for sxx in sfrom to starget {
            txt.print_b(sxx)
            txt.spc()
        }
        txt.print("done\n")
        for sxx in starget downto sfrom {
            txt.print_b(sxx)
            txt.spc()
        }
        txt.print("done\n")

        uword wtarget = 10
        uword wfrom = 65530

        uword ww
        for ww in wfrom to wtarget {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.print("done\n")
        for ww in wtarget downto wfrom {
            txt.print_uw(ww)
            txt.spc()
        }
        txt.print("done\n")

        word swtarget = -32760
        word swfrom = 32760
        word sww
        for sww in swfrom to swtarget {
            txt.print_w(sww)
            txt.spc()
        }
        txt.print("done\n")
        for sww in swtarget downto swfrom {
            txt.print_w(sww)
            txt.spc()
        }
        txt.print("done\n")

        ; TODO all of the above with stepsize 2 / -2
    }
}

