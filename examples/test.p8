%import c64lib
%import c64utils
%import c64flt
%zeropage dontuse

main {

    sub start() {

        c64scr.print("\nbreakpoint after this.")
        %breakpoint
        c64scr.print("\nyou should see no errors above.")
    }
}
