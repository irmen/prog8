%import strings
%import textio
%option no_sysinit
%zeropage basicsafe

; some bank switching on the C64.  See https://www.c64-wiki.com/wiki/Bank_Switching

main {
    sub start() {
        ; copy basic rom to ram and replace ready prompt
        sys.memcopy($a000, $a000, $2000)
        void strings.copy(iso:"HELLO!\r", $a378)

        txt.print("8 bytes at $f000 (kernal rom):\n")
        for cx16.r0 in $f000 to $f007 {
            txt.print_ubhex(@(cx16.r0), false)
            txt.spc()
        }
        txt.nl()

        ; store some other data in the RAM below those kernal ROM locations
        ; switch off kernal rom to see those bytes
        ; we cannot print during this time and the IRQ has to be disabled temporarily as well.
        void strings.copy("hello !?", $f000)
        sys.set_irqd()
        c64.banks(%101)     ; switch off roms
        ubyte[8] buffer
        sys.memcopy($f000, &buffer, 8)
        c64.banks(%111)     ; kernal rom back on
        sys.clear_irqd()
        txt.print("8 bytes at $f000 (ram this time):\n")
        for cx16.r0L in buffer {
            txt.print_ubhex(cx16.r0L, false)
            txt.spc()
        }
        txt.nl()


        ; we can switch off the basic rom now, but this is not persistent after program exit
        c64.banks(%110)

        ; ...so we print a message for the user to do it manually to see the changed prompt.
        txt.print("\ntype: poke 1,54\nto switch off basic rom :-)\n")
    }
}
