%import textio
%import diskio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        uword buffer = memory("buffer", 100, 0)
        uword buffer2 = memory("buffer2", 100, 0)
        sys.memset(buffer, 100, 0)
        sys.memset(buffer2, 100, 0)
        str contents = petscii:"<- this is the contents of the file ->"

        txt.print("string size=")
        txt.print_uw(len(contents))
        txt.nl()
        txt.print(contents)
        txt.nl()
        txt.print_ubhex(contents[3],true)
        txt.spc()
        txt.print_ubhex(contents[4],true)
        txt.spc()
        txt.print_ubhex(contents[5],true)
        txt.nl()
        txt.nl()

        diskio.save("@:testfile", contents, len(contents))
        uword end_address = diskio.load_raw("testfile", buffer)
        uword size = end_address - buffer
        txt.print("file size=")
        txt.print_uw(size)
        txt.nl()
        txt.print_uwhex(peekw(buffer), true)
        txt.spc()
        txt.print(buffer+2)
        txt.nl()
        txt.print_ubhex(buffer[2+3],true)
        txt.spc()
        txt.print_ubhex(buffer[2+4],true)
        txt.spc()
        txt.print_ubhex(buffer[2+5],true)
        txt.nl()
        txt.nl()

        diskio.f_open("testfile")
        size = diskio.f_read_all(buffer2)
        diskio.f_close()
        txt.print("file size=")
        txt.print_uw(size)
        txt.nl()
        txt.print_uwhex(peekw(buffer2), true)
        txt.spc()
        txt.print(buffer2+2)
        txt.nl()
        txt.print_ubhex(buffer2[2+3],true)
        txt.spc()
        txt.print_ubhex(buffer2[2+4],true)
        txt.spc()
        txt.print_ubhex(buffer2[2+5],true)
        txt.nl()
        txt.nl()
    }
}
