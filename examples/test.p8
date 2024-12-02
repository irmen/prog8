%import textio
%import diskio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        txt.print("----with read 1:\n")
        testread1()
        txt.print("----with readline:\n")
        testreadline()
    }

    sub testread1() {
        if not diskio.f_open("lines.txt")
            return
        defer diskio.f_close()

        str buffer = " "
        while 1==diskio.f_read(&buffer, 1) {
            txt.chrout(buffer[0])
        }
    }

    sub testreadline() {
        if not diskio.f_open("lines.txt")
            return
        defer diskio.f_close()

        str buffer = " "*80
        ubyte length, status
        do {
            length, status = diskio.f_readline(&buffer)
            if length!=0 {
                txt.print(buffer)
                txt.nl()
            }
        } until status!=0
    }
}
