%import textio
%import string
%zeropage basicsafe
%option no_sysinit

main {


    sub start() {
        str name = "abcdef"

        uword ptr = &name
        ubyte cc

        cc = @(ptr)
        txt.chrout(cc)
        txt.nl()
        cc = @(ptr+1)
        txt.chrout(cc)
        txt.nl()
        cc = @(ptr+2)
        txt.chrout(cc)
        txt.nl()
        txt.nl()

        cc=0

        txt.chrout(@(ptr)+cc)
        txt.chrout(@(ptr+1)+cc)
        txt.chrout(@(ptr+2)+cc)
        txt.nl()

        @(ptr) = '1'
        @(ptr+1) = '2'
        @(ptr+2) = '3'
        txt.print(name)
        txt.nl()

        cc=0
        @(ptr+cc) = 'a'
        @(ptr+cc+1) = 'b'
        @(ptr+cc+2) = 'c'
        txt.print(name)
        txt.nl()
    }

}
