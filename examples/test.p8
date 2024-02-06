%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared xx
        ubyte @shared yy

        xx++
        yy++
        xx--
        yy--

        xx+=1
        yy-=1
    }
}
