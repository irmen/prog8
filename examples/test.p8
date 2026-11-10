%import textio
%zeropage basicsafe

main {

    struct command {
        pointer name
        ubyte   parms
        ubyte   handler
    }


    sub start() {
        command[] commands = [
                ["arc",2,1],
                ["back",1,2]
        ]

        ^^command x = ["second",2,1 ]

        x.parms++

        ^^command cmd = &commands[1]

        txt.print(cmd.name)
        txt.spc()
        txt.print_ub(cmd.parms)
        txt.nl()


        poke(cmd.name+1, '@')
        cmd.parms = 199

        txt.print(cmd.name)
        txt.spc()
        txt.print_ub(cmd.parms)
        txt.nl()
    }
}
