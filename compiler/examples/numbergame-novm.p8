%import c64utils

~ main {
    sub start()  {
        str   name    = "                    "
        str   guess   = "000000"
        ubyte secretnumber = rnd() % 100

        c64scr.print_string("Let's play a number guessing game!\n")
        c64scr.print_string("Enter your name: ")
        vm_input_str(name)
        c64scr.print_string("\nHello, ")
        c64scr.print_string(name)
        c64scr.print_string(".\nI am thinking of a number from 1 to 100! You'll have to guess it!\n")

        for ubyte attempts_left in 10 to 1 step -1 {
            c64scr.print_string("\nYou have ")
            vm_write_num(attempts_left)
            c64scr.print_string(" guess")
            if attempts_left>1  c64scr.print_string("es")
            c64scr.print_string(" left. What is your next guess? ")
            vm_input_str(guess)
            ubyte guessednumber = str2ubyte(guess)
            if guessednumber==secretnumber {
                c64scr.print_string("\nYou guessed it, impressive!\n")
                c64scr.print_string("Thanks for playing.\n")
                return
            } else {
                c64scr.print_string("That is too ")
                if guessednumber<secretnumber
                    c64scr.print_string("low!\n")
                else
                    c64scr.print_string("high!\n")
            }
        }

        c64scr.print_string("\nToo bad! My number was: ")
        vm_write_num(secretnumber)
        c64scr.print_string(".\n")
        return
    }
}
