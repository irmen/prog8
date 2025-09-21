%import syslib
%import textio
%zeropage basicsafe
%option no_sysinit

;  Create a custom character set on the C64.

main {

    sub start() {
        txt.color(1)
        txt.print("creating charset...\n")
        charset.make_custom_charset()

        ; activate the new charset in RAM
        const ubyte PAGE1 = ((cbm.Screen >> 6) & $F0) | ((charset.CHARSET >> 10) & $0E)
        c64.VMCSB = PAGE1

        txt.print("\n @ @ @ @\n")
    }
}

charset {
    const uword CHARSET = $2000

    sub copy_rom_charset() {
        ; copies the charset from ROM to RAM so we can modify it

        sys.set_irqd()
        c64.banks(%011)  ; enable CHAREN, so the character rom accessible at $d000
        sys.memcopy($d000, CHARSET, 256*8*2)  ; copy the charset to RAM
        c64.banks(%111)  ; enable I/O registers again
        sys.clear_irqd()
    }

    sub make_custom_charset() {
        copy_rom_charset()

        ; make all characters italic
        ubyte c
        for c in 0 to 255 {
            uword ptr = CHARSET + c*$0008
            @(ptr) >>= 2
            @(ptr+1) >>= 2
            @(ptr+2) >>= 1
            @(ptr+3) >>= 1
            ;@(ptr+4) >>= 0
            ;@(ptr+5) >>= 0
            @(ptr+6) <<= 1
            @(ptr+7) <<= 1

            ptr = CHARSET + 256*8 + c*$0008
            @(ptr) >>= 2
            @(ptr+1) >>= 2
            @(ptr+2) >>= 1
            @(ptr+3) >>= 1
            ;@(ptr+4) >>= 0
            ;@(ptr+5) >>= 0
            @(ptr+6) <<= 1
            @(ptr+7) <<= 1
        }

        ; add a smiley over the '@'

        ubyte[] smiley = [
            %00111100,
            %01000010,
            %10100101,
            %10000001,
            %10100101,
            %10011001,
            %01000010,
            %00111100
        ]

        sys.memcopy(smiley, CHARSET, len(smiley))

    }
}
