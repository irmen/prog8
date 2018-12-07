%import c64utils
%import mathlib


~ main {
    sub start()  {
        str   name    = "????????????????????????????????????????"
        str   guess   = "??????????"
        ubyte secretnumber = rnd() % 100

        c64.VMCSB |= 2  ; switch lowercase chars
        c64scr.print_string("Please introduce yourself: ")
        c64scr.input_chars(name)
        c64scr.print_string("\n\nHello, ")
        c64scr.print_string(name)
        c64scr.print_string(".\nLet's play a number guessing game.\nI am thinking of a number from 1 to 100!You'll have to guess it!\n")

        for ubyte attempts_left in 10 to 1 step -1 {
            c64scr.print_string("\nYou have ")
            c64scr.print_byte_decimal(attempts_left)
            c64scr.print_string(" guess")
            if attempts_left>1  c64scr.print_string("es")
            c64scr.print_string(" left.\nWhat is your next guess? ")
            c64scr.input_chars(guess)
            ubyte guessednumber = str2ubyte(guess)
            if guessednumber==secretnumber {
                c64scr.print_string("\n\nYou guessed it, impressive!\n")
                c64scr.print_string("Thanks for playing, ")
                c64scr.print_string(name)
                c64scr.print_string(".\n")
                return
            } else {
                c64scr.print_string("\n\nThat is too ")
                if guessednumber<secretnumber
                    c64scr.print_string("low!\n")
                else
                    c64scr.print_string("high!\n")
            }
        }

        c64scr.print_string("\nToo bad! My number was: ")
        c64scr.print_byte_decimal(secretnumber)
        c64scr.print_string(".\n")
        return
    }
}
