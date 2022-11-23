; this program shows the use of the f_seek function to seek to a position in an opened file.
; (this only works on Commander X16 DOS. on sdcard, not on host filesystem.)

%import diskio
%import cx16diskio
%import textio
%zeropage basicsafe
%option no_sysinit

main {
    str FILENAME = "seektestfile.bin"

    sub start() {
        txt.print("writing data file...\n")
        uword total=0
        diskio.delete(8, FILENAME)
        if diskio.f_open_w(8, FILENAME) {
            repeat 1000 {
                str text = "hello world."
                void diskio.f_write(text, string.length(text))
                total += string.length(text)
            }
            diskio.f_close_w()
            txt.print("written size=")
            txt.print_uw(total)
            txt.nl()
        } else {
            txt.print("error: ")
            txt.print(diskio.status(8))
            sys.exit(1)
        }

        read_last_bytes()

;        txt.print("\nseeking to 11992 and writing a few bytes...\n")
;        if diskio.f_open_w(8, FILENAME) {
;            cx16diskio.f_seek_w(0,0,msb(11992),lsb(11992))
;            txt.print(diskio.status(8))
;            txt.nl()
;            void diskio.f_write("123", 3)
;            diskio.f_close_w()
;        } else {
;            txt.print("error: ")
;            txt.print(diskio.status(8))
;            sys.exit(1)
;        }
;
;        read_last_bytes()
    }

    sub read_last_bytes() {
        ; read the last 10 bytes of the 12000 bytes file
        ubyte[256] buffer
        uword total = 0
        uword size
        txt.print("\nseeking to 11990 and reading...\n")
        if diskio.f_open(8, FILENAME) {
            cx16diskio.f_seek(0, 11990)
            do {
                size = diskio.f_read(buffer, sizeof(buffer))
                total += size
            } until size==0
            diskio.f_close()
            txt.print("size read=")
            txt.print_uw(total)
            txt.nl()
            buffer[lsb(total)] = 0
            txt.print("buffer read=")
            ubyte idx
            for idx in 0 to lsb(total-1) {
                txt.print_ubhex(buffer[idx], false)
                txt.spc()
            }
            txt.spc()
            txt.chrout('{')
            txt.print(buffer)
            txt.chrout('}')
            txt.nl()
        } else {
            txt.print("error: ")
            txt.print(diskio.status(8))
            sys.exit(1)
        }
    }
}
