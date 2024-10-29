%import textio
%import diskio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        txt.lowercase()

        uword buffer = memory("buffer", 16000, 0)
        uword line = 0

        if diskio.f_open("gradlew.bat") {
            uword size = diskio.f_read(buffer, 1000)
            txt.print_uw(size)
            txt.nl()
            diskio.f_close()
        }

        if diskio.f_open("gradlew.bat") {
            size = diskio.f_read_all(buffer)
            txt.print_uw(size)
            txt.nl()

            diskio.f_close()
        }

        if diskio.f_open("gradlew.bat") {
            ubyte linesize, status
            repeat {
                linesize = diskio.f_readline(buffer)
                if_cs {
                    line++
                } else break
            }
            diskio.f_close()

            txt.print_uw(line)
            txt.nl()
        }

        if diskio.f_open_w("result.txt") {
            void diskio.f_write("line 1\n", 7)
            void diskio.f_write("line 2\n", 7)
            void diskio.f_write("line 3\n", 7)
            diskio.f_close_w()
        }
    }


}
