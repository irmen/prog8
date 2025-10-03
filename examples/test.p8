%import textio
%zeropage basicsafe
%import bcd

main {
    sub start() {
        long highscore = $50999

        highscore = bcd.addl(highscore, 1)
        txt.print_ulhex(highscore, false)       ; prints 00051000, avoiding costly decimal conversion
    }
}
