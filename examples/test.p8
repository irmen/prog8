%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ^^ubyte @shared ubptr = 2000
        uword @shared ubptr2 = 3000

        init()
        reads()
        prints()
        writes()
        reads()
        prints()

        sub init() {
            @(2000) = 100
            @(2001) = 101
            @(2002) = 102
            @(2500) = 109
            @(3000) = 200
            @(3001) = 201
            @(3002) = 202
            @(3500) = 209
        }

        sub prints() {
            txt.print_ub(cx16.r0L)
            txt.spc()
            txt.print_ub(cx16.r1L)
            txt.spc()
            txt.print_ub(cx16.r2L)
            txt.spc()
            txt.print_ub(cx16.r3L)
            txt.nl()
        }

        sub reads() {
            cx16.r0L = ubptr^^
            cx16.r1L = @(ubptr2)
            cx16.r2L = @(ubptr2+1)
            cx16.r3L = @(ubptr2+500)
        }

        sub writes() {
            ubptr^^ = 55
            @(ubptr2) = 66
            @(ubptr2+1) = 77
            @(ubptr2+500) = 88
        }
    }
}
