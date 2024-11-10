%import textio
%option no_sysinit
%zeropage basicsafe
%address $f200
%output raw
%launcher none

main {
    sub start() {
        txt.print_uwhex(cbm.MEMTOP(0, true), true)
    }
}
