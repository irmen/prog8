%import textio
%import diskio

%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        txt.iso()
        cx16.iso_cursor_char(iso:'_')
    }
}
