%import math
%import string
%import emudbg
%import palette
%import floats
%import textio
%zeropage dontuse

main {
    sub start() {
        emudbg.console_value1(123)
        emudbg.console_value2(99)
        emudbg.console_chrout('@')
        emudbg.console_chrout('h')
        emudbg.console_chrout('e')
        emudbg.console_chrout('l')
        emudbg.console_chrout('l')
        emudbg.console_chrout('o')
        emudbg.console_chrout('\n')
    }
}
