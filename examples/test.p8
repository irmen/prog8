%import textio

%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        txt.print_bool(cx16.mouse_present())
    }
}
