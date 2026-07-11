%import floats
%import textio
%zeropage basicsafe

main {
    sub start() {
        qemu.bootinfo_dump()
        bootinfo_print()
;        str input = "?"*80
;        txt.print("Enter your name: ")
;        ubyte length=txt.input_chars(input)
;        txt.print("\nHello, ")
;        txt.print(input)
;        txt.print("!\n")
;        txt.print_ub(length)
;        txt.nl()
    }

    sub bootinfo_print() {
        long ptr = qemu.bootinfo_ptr()
        repeat {
            uword tag = peekw(ptr)
            if tag == 0
                break
            uword size = peekw(ptr + 2)
            txt.print("REC ")
            txt.print_uwhex(tag, false)
            txt.print(" size=")
            txt.print_uwhex(size, false)
            txt.print(" data=")
            uword ii = 0
            uword data_count = size - 4
            repeat data_count {
                ubyte b = @(ptr + 4 + ii)
                txt.print_ubhex(b, false)
                txt.print(" ")
                ii++
            }
            txt.nl()
            ptr = qemu.bootinfo_next(ptr)
        }
        txt.print("--- end bootinfo ---")
    }

}
