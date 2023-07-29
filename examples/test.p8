%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte var = 0

        when var {
            1 -> txt.print("one")
            2 -> txt.print("two")
            0 -> {
            }
            else -> txt.print("other")
        }
	}
}
