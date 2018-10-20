~ main {
    sub start()  {
        str   name    = "                    "
        str   guess   = "000000"
        ubyte secretnumber = rnd() % 100

        vm_write_str("Let's play a number guessing game!\n")
        vm_write_str("Enter your name: ")
        vm_input_str(name)
        vm_write_str("\nHello, ")
        vm_write_str(name)
        vm_write_str(".\nI am thinking of a number from 1 to 100! You'll have to guess it!\n")

        for ubyte attempts_left in 10 to 1 step -1 {
            vm_write_str("\nYou have ")
            vm_write_num(attempts_left)
            vm_write_str(" guess")
            if attempts_left>1  vm_write_str("es")
            vm_write_str(" left. What is your next guess? ")
            vm_input_str(guess)
            ubyte guessednumber = str2ubyte(guess)
            if guessednumber==secretnumber {
                vm_write_str("\nYou guessed it, impressive!\n")
                vm_write_str("Thanks for playing.\n")
                return
            } else {
                vm_write_str("That is too ")
                if guessednumber<secretnumber
                    vm_write_str("low!\n")
                else
                    vm_write_str("high!\n")
            }
        }

        vm_write_str("\nToo bad! My number was: ")
        vm_write_num(secretnumber)
        vm_write_str(".\n")
        return
    }
}
