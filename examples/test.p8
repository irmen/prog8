%import diskio
%import textio
%zeropage basicsafe

main {

    sub start() {
        void diskio.f_open("t8s.wav")
        long size, pos

        pos, size = diskio.f_tell()
        txt.print_l(pos)
        txt.spc()
        txt.print_l(size)
        txt.nl()

        diskio.f_seek(999999)
        pos, size = diskio.f_tell()
        txt.print_l(pos)
        txt.spc()
        txt.print_l(size)
        txt.nl()

        long lba, cluster
        lba, cluster = diskio.f_fatlba()
        txt.print_l(lba)
        txt.spc()
        txt.print_l(cluster)
        txt.nl()
        diskio.f_seek(0)
        lba, cluster = diskio.f_fatlba()
        txt.print_l(lba)
        txt.spc()
        txt.print_l(cluster)
        txt.nl()

        diskio.f_close()
    }
}
