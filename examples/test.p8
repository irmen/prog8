%option no_sysinit
%zeropage kernalsafe
%import textio
%zpallowed 224,255

main {
    uword @shared @requirezp zpvar1
    uword @shared @requirezp zpvar2
    uword @shared @requirezp zpvar3
    uword @shared @requirezp zpvar4
    uword @shared @requirezp zpvar5
    uword @shared @requirezp @dirty dzpvar1
    uword @shared @requirezp @dirty dzpvar2
    uword @shared @requirezp @dirty dzpvar3
    uword @shared @requirezp @dirty dzpvar4
    uword @shared @requirezp @dirty dzpvar5
    uword @shared @nozp var1
    uword @shared @nozp var2
    uword @shared @nozp var3
    uword @shared @nozp var4
    uword @shared @nozp var5
    uword @shared @nozp @dirty dvar1
    uword @shared @nozp @dirty dvar2
    uword @shared @nozp @dirty dvar3
    uword @shared @nozp @dirty dvar4
    uword @shared @nozp @dirty dvar5

    sub start() {
        txt.print("address start of zpvars: ")
        txt.print_uw(&zpvar1)
        txt.nl()
        txt.print("address start of normal vars: ")
        txt.print_uw(&var1)
        txt.nl()

        txt.print("non-dirty zp should all be 0: ")
        txt.print_uw(zpvar1)
        txt.spc()
        txt.print_uw(zpvar2)
        txt.spc()
        txt.print_uw(zpvar3)
        txt.spc()
        txt.print_uw(zpvar4)
        txt.spc()
        txt.print_uw(zpvar5)
        txt.nl()
        txt.print("non-dirty should all be 0:    ")
        txt.print_uw(var1)
        txt.spc()
        txt.print_uw(var2)
        txt.spc()
        txt.print_uw(var3)
        txt.spc()
        txt.print_uw(var4)
        txt.spc()
        txt.print_uw(var5)
        txt.nl()

        txt.print("dirty zp may be random: ")
        txt.print_uw(dzpvar1)
        txt.spc()
        txt.print_uw(dzpvar2)
        txt.spc()
        txt.print_uw(dzpvar3)
        txt.spc()
        txt.print_uw(dzpvar4)
        txt.spc()
        txt.print_uw(dzpvar5)
        txt.nl()
        txt.print("dirty may be random: ")
        txt.print_uw(dvar1)
        txt.spc()
        txt.print_uw(dvar2)
        txt.spc()
        txt.print_uw(dvar3)
        txt.spc()
        txt.print_uw(dvar4)
        txt.spc()
        txt.print_uw(dvar5)
        txt.nl()

        repeat {}
    }
}
