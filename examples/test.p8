%import test_stack
%import textio
%import diskio
%import string
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        if diskio.f_open(8, "romdis.asm") {
            uword buffer = memory("diskbuffer", $1000)

            uword linenr = 0
            c64.SETTIM(0,0,0)

            while not c64.READST() {
                ubyte length = diskio.f_readline(buffer)
                if length {
                    linenr++
                    if not lsb(linenr)
                        txt.chrout('.')
                } else
                    goto io_error
            }

io_error:
            txt.print("\n\n\n\n\n\n\nnumber of lines: ")
            txt.print_uw(linenr)
            txt.nl()
            diskio.f_close()
        }

        txt.print(diskio.status(8))
        txt.nl()

        txt.print("\ntime: ")
        txt.print_uw(c64.RDTIM16())
        txt.nl()
    }

}
