%import strings
%import textio
%import diskio
%zeropage basicsafe

main {
    sub start() {
        ^^ubyte sentence = "the quick brown fox jumps over the lazy dog."
        ubyte[] whitespace = [ 9, 10, 13, 32, 160, 0 ]
        txt.lowercase()

        ^^ubyte token = strings.next_token(sentence, whitespace)
        while token != 0 {
            txt.print(token)
            txt.nl()
            token = strings.next_token(0, whitespace)
        }

        uword[4] @nosplit parts
        ubyte numparts

        numparts = strings.split(0, parts, len(parts))
        printparts(numparts, parts)

        numparts = strings.split("", parts, len(parts))
        printparts(numparts, parts)

        numparts = strings.split("hello", parts, len(parts))
        printparts(numparts, parts)

        numparts = strings.split("the quick brown fox jumps over the lazy dog", parts, len(parts))
        printparts(numparts, parts)

        numparts = strings.split("   the   quick   brown   fox  jumps  over  the  lazy  dog    ", parts, len(parts))
        printparts(numparts, parts)
    }

    sub printparts(ubyte numparts, ^^uword parts) {
        txt.print_ub(numparts)
        txt.print(" parts: ")
        if numparts > 0 {
            for cx16.r0L in 0 to numparts-1 {
                txt.print(parts[cx16.r0L])
                txt.print(",")
            }
        }
        txt.nl()
    }
}
