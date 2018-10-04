%output prg

~ main {
    sub start()  {
        str   name    = "                    "
        str   guess   = "000000"
        byte  guessednumber
        byte  attempts_left

        _vm_write_str("Let's play a number guessing game!\n")
        _vm_write_str("Enter your name: ")
        _vm_input_str(name)
        _vm_write_str("\nHello, ")
        _vm_write_str(name)
        _vm_write_str(".\nI am thinking of a number from 1 to 100! You'll have to guess it!\n")

        byte secretnumber = rnd() % 100

        for attempts_left in 10 to 1 step -1 {
            _vm_write_str("\nYou have ")
            _vm_write_num(attempts_left)
            _vm_write_str(" guess")
            if attempts_left>1  _vm_write_str("es")
            _vm_write_str(" left. What is your next guess? ")
            _vm_input_str(guess)
            guessednumber = str2byte(guess)
            if guessednumber==secretnumber {
                _vm_write_str("\nYou guessed it, impressive!\n")
                _vm_write_str("Thanks for playing.\n")
                return
            } else {
                _vm_write_str("That is too ")
                if guessednumber<secretnumber
                    _vm_write_str("low!\n")
                else
                    _vm_write_str("high!\n")
            }
        }

        _vm_write_str("\nToo bad! My number was: ")
        _vm_write_num(secretnumber)
        _vm_write_str(".\n")
        return
    }
}
