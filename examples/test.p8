%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared x,y,z
        ubyte @shared k,l,m = 42
        uword @shared r,s,t = sys.progend()
    }
}
