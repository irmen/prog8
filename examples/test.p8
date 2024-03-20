%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    str scanline_buf = "?"* 20

    sub start() {
        if diskio.f_open("test.prg") and diskio.f_read(scanline_buf, 2)==2
            cx16.r0++

        if diskio.f_open("test.prg") or diskio.f_read(scanline_buf, 2)==2
            cx16.r0++

        if diskio.f_open("test.prg") xor diskio.f_read(scanline_buf, 2)==2
            cx16.r0++
    }
}
