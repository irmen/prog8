; this program shows the use of the f_seek function to seek to a position in an opened file.
; (this only works on Commander X16 DOS. on sdcard, not on host filesystem.)

%import diskio
%import textio
%zeropage basicsafe
%option no_sysinit

main {
    uword megabuffer = memory("megabuffer", 20000, 256)

    sub start() {

        txt.print("writing data file (drive 8)...\n")
        uword total=0
        if diskio.f_open_w("@:seektestfile.bin") {
            repeat 100 {
                str text = "hello world.*"
                void diskio.f_write(text, strings.length(text))
                total += strings.length(text)
            }
            diskio.f_close_w()
            txt.print("written size=")
            txt.print_uw(total)
            txt.nl()
        } else {
            txt.print("error: ")
            txt.print(diskio.status())
            sys.exit(1)
        }

        read_last_bytes()

        txt.print("\nseeking to 1292 and writing a few bytes...\n")
        if diskio.f_open_w_seek("seektestfile.bin") {
            diskio.f_seek_w(1292)
            void diskio.f_write("123", 3)
            diskio.f_close_w()
        } else {
            txt.print("error: ")
            txt.print(diskio.status())
            sys.exit(1)
        }

        read_last_bytes()
    }

    sub read_last_bytes() {
        ; read the last 10 bytes of the 1300 bytes file

        ; first a test that a regular read still reads 1300 bytes
        uword total = 0
        uword size
        txt.print("\nreading...\n")
        if diskio.f_open("seektestfile.bin") {
            size = diskio.f_read_all(megabuffer)
            diskio.f_close()
            txt.print("size read:")
            txt.print_uw(size)
            txt.nl()
        } else {
            txt.print("error!\n")
            sys.exit(1)
        }

        ; now the actual seek and read of the last few bytes
        txt.print("\nseeking to 1290 and reading...\n")
        if diskio.f_open("seektestfile.bin") {
            diskio.f_seek(1290)
            uword ptr = megabuffer
            do {
                size = diskio.f_read(ptr, 255)
                total += size
                ptr += size
            } until size==0
            diskio.f_close()
            txt.print("size read=")
            txt.print_uw(total)
            txt.nl()
            megabuffer[lsb(total)] = 0
            txt.print("buffer read=")
            ubyte idx
            for idx in 0 to lsb(total-1) {
                txt.print_ubhex(megabuffer[idx], false)
                txt.spc()
            }
            txt.print("\nas text: \"")
            txt.print(megabuffer)
            txt.print("\"\n")
        } else {
            txt.print("error: ")
            txt.print(diskio.status())
            sys.exit(1)
        }
    }
}
