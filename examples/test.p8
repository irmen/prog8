%import textio
%import diskio
%zeropage basicsafe

main {
    sub start() {

        txt.print("pwd: ")
        txt.print(diskio.curdir())
        txt.print("\ndisk name: ")
        uword name = diskio.diskname()
        if name
            txt.print(name)
        else
            txt.print("!error!")
    }
}
