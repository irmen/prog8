%import textio
%import diskio

%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        str command = "u0>b1"
        diskio.send_command(command)
        ;txt.print(diskio.status())
        txt.print_bool(diskio.status_code()==0)
        txt.nl()
        txt.print_bool(status_code()==0)
        txt.nl()
        ;txt.print_bool(diskio.status_code()==0)
    }

    sub status_code() -> ubyte {
        cx16.r0++
        return 30
    }
}
