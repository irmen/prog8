%import textio
%import diskio
%import string
%zeropage basicsafe
%option no_sysinit

main {


    sub start() {

        diskio.directory(8)
        diskio.save(8, "blabla", $2000, 1024)
        diskio.directory(8)
        diskio.rename(8, "blabla", "newname")
        diskio.directory(8)
        diskio.delete(8, "newname")
        diskio.directory(8)

        txt.print("---------------------------------\n")
    }
}
