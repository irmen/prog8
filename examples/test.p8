%import test_stack
%import textio
%import diskio
%import string
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        ubyte[40] input_line

        if diskio.f_open(8, "romdis.asm") {
            uword line=0
            repeat 5 {
                ubyte length = diskio.f_readline(input_line)
                if length {
                    line++
                    txt.print_uw(line)
                    txt.chrout(':')
                    txt.print_ub(length)
                    txt.print(":[")
                    ubyte xx
                    for xx in 0 to length-1 {
                        txt.print_ubhex(input_line[xx], 1)
                        txt.chrout(' ')
                    }
                    ; txt.print(&input_line)
                    txt.print("]\n")
                    ; textparse.process_line()
                    if c64.READST()         ; TODO also check STOP key
                        break
                } else
                    break
            }
            diskio.f_close()
        }
    }


}
