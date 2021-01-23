%import textio
%import diskio
%import string
%zeropage basicsafe
%option no_sysinit

main {


    sub start() {
        ; TODO test memcopy
        ; counts: 0, 1, 2, 254, 255, 256, 257, 512, 1000

        uword buffer=memory("buffer",1000)
        uword ones=memory("ones",1000)

        sys.memset(buffer, 1000, '.')
        @(buffer) = '<'
        @(buffer+255) = '>'
        @(buffer+256) = '<'
        @(buffer+511) = '>'
        @(buffer+512) = '<'
        @(buffer+767) = '>'
        @(buffer+768) = '<'
        @(buffer+999) = '!'
        sys.memset(ones, 1000, '*')

        txt.clear_screen()
        txt.print("\n\n\n\n\n\n\n\n\n")

        sys.memcopy(ones, buffer, 999)

        uword scr = $0400
        uword ix
        for ix in 0 to 999 {
            @(scr) = @(buffer+ix)
            scr++
        }
    }
}
