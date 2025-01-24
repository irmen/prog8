%address  $A000
%memtop   $C000
%output   library

%import textio


main {
    sub start() {
        txt.print("hello from library\n")
    }
}
