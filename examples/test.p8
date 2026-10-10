%encoding iso
%import textio
%import bcd
%zeropage basicsafe

main {
    sub start() {
        txt.iso()

        txt.print("bcd addition (hex: each nibble is a decimal digit)\n")
        check_byte_add($25, $13, $38)
        check_byte_add($58, $41, $99)

        txt.print("\nbcd subtraction\n")
        check_byte_sub($64, $27, $37)
        check_byte_sub($80, $29, $51)

        txt.print("\nbcd word addition\n")
        check_word_add($1234, $5678, $6912)

        txt.print("\nbcd word subtraction\n")
        check_word_sub($8000, $1234, $6766)

        txt.print("\nbcd long addition\n")
        check_long_add($12345678, $87654321, $99999999)

        txt.print("\nbcd long subtraction\n")
        check_long_sub($98765432, $12345678, $86419754)

        txt.print("\nbcd long in-place addition\n")
        check_long_add_in_place($12345678, $87654321, $99999999)

        txt.print("\nbcd long in-place subtraction\n")
        check_long_sub_in_place($98765432, $12345678, $86419754)

        sys.poweroff_system()
    }

    sub check_byte_add(ubyte a, ubyte b, ubyte expected) {
        ubyte result = bcd.addub(a, b)
        txt.print_ubhex(a, false)
        txt.print(" + ")
        txt.print_ubhex(b, false)
        txt.print(" = ")
        txt.print_ubhex(result, false)
        txt.print("   expected ")
        txt.print_ubhex(expected, false)
        print_verdict(result == expected)
    }

    sub check_byte_sub(ubyte a, ubyte b, ubyte expected) {
        ubyte result = bcd.subub(a, b)
        txt.print_ubhex(a, false)
        txt.print(" - ")
        txt.print_ubhex(b, false)
        txt.print(" = ")
        txt.print_ubhex(result, false)
        txt.print("   expected ")
        txt.print_ubhex(expected, false)
        print_verdict(result == expected)
    }

    sub check_word_add(uword a, uword b, uword expected) {
        uword result = bcd.adduw(a, b)
        txt.print_uwhex(a, false)
        txt.print(" + ")
        txt.print_uwhex(b, false)
        txt.print(" = ")
        txt.print_uwhex(result, false)
        txt.print("   expected ")
        txt.print_uwhex(expected, false)
        print_verdict(result == expected)
    }

    sub check_word_sub(uword a, uword b, uword expected) {
        uword result = bcd.subuw(a, b)
        txt.print_uwhex(a, false)
        txt.print(" - ")
        txt.print_uwhex(b, false)
        txt.print(" = ")
        txt.print_uwhex(result, false)
        txt.print("   expected ")
        txt.print_uwhex(expected, false)
        print_verdict(result == expected)
    }

    sub check_long_add(long a, long b, long expected) {
        long result = bcd.addl(a, b)
        txt.print_ulhex(a, false)
        txt.print(" + ")
        txt.print_ulhex(b, false)
        txt.print(" = ")
        txt.print_ulhex(result, false)
        txt.print("   expected ")
        txt.print_ulhex(expected, false)
        print_verdict(result == expected)
    }

    sub check_long_sub(long a, long b, long expected) {
        long result = bcd.subl(a, b)
        txt.print_ulhex(a, false)
        txt.print(" - ")
        txt.print_ulhex(b, false)
        txt.print(" = ")
        txt.print_ulhex(result, false)
        txt.print("   expected ")
        txt.print_ulhex(expected, false)
        print_verdict(result == expected)
    }

    sub check_long_add_in_place(long initial, long value, long expected) {
        long @shared result = initial
        bcd.addtol(&result, value)
        txt.print_ulhex(result, false)
        txt.print("   expected ")
        txt.print_ulhex(expected, false)
        print_verdict(result == expected)
    }

    sub check_long_sub_in_place(long initial, long value, long expected) {
        long @shared result = initial
        bcd.subfroml(&result, value)
        txt.print_ulhex(result, false)
        txt.print("   expected ")
        txt.print_ulhex(expected, false)
        print_verdict(result == expected)
    }

    sub print_verdict(bool ok) {
        if ok
            txt.print("   ok\n")
        else
            txt.print("   fail\n")
    }
}
