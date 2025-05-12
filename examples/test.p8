%import textio
%zeropage basicsafe

main {
    sub start() {
        test(0)
        test(1)
        test(2)
        test(3)
        test(42)
    }

    sub test(ubyte value)  {
        when value {
            0 -> goto first
            1 -> goto second
            2 -> goto third
            3 -> goto fourth
            4 -> goto third
            else -> goto other
        }

        ; 65c02: 3+ options better as on:
        ; 6502: 5+ options better as on:

        ;on value goto (first, second, third, fourth, third, fourth)

        sub first() {
            cx16.r0++
            txt.print("first\n")
        }
        sub second() {
            cx16.r0++
            txt.print("second\n")
        }
        sub third() {
            cx16.r0++
            txt.print("third\n")
        }
        sub fourth() {
            cx16.r0++
            txt.print("fourth\n")
        }
        sub other() {
            cx16.r0++
            txt.print("other\n")
        }
    }
}
