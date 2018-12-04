%output prg
%import c64lib
%import c64utils

~ main {
    sub start()  {
        str   name    = "????????????????????????????????????????"
        str   guess   = "??????????"
        ubyte  secretnumber = 0
        ubyte  attempts_left = 10
        memory uword freadstr_arg = $22		; argument for FREADSTR
        uword testword

        ; greeting
        c64scr.print_string("Enter your name: ")
        Y = c64scr.input_chars(name)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        c64scr.print_string("Hello, ")
        c64scr.print_string(name)
        c64.CHROUT('.')
        c64.CHROUT('\n')

        ; create a secret random number from 1-100
        c64.RNDA(0)             ; fac = rnd(0)
        c64.MUL10()             ; fac *= 10
        c64.MUL10()             ; .. and now *100
        c64.FADDH()             ; add 0.5..
        c64.FADDH()             ;   and again, so +1 total
        A, Y = c64flt.GETADRAY()
        secretnumber = A

        c64scr.print_string("I am thinking of a number from 1 to 100!You'll have to guess it!\n")

ask_guess:
        c64scr.print_string("\nYou have ")
        c64scr.print_byte_decimal(attempts_left)
        c64scr.print_string(" guess")
        if(attempts_left>0) c64scr.print_string("es")

        c64scr.print_string(" left.\nWhat is your next guess? ")
        Y = c64scr.input_chars(guess)
        c64.CHROUT('\n')
        freadstr_arg = guess
        c64.FREADSTR(A)
        A, Y = c64flt.GETADRAY()
        if(A==secretnumber) {
            c64scr.print_string("\nThat's my number, impressive!\n")
            goto goodbye
        }
        c64scr.print_string("That is too ")
        if(A > secretnumber)
            c64scr.print_string("low!\n")
        else
            c64scr.print_string("high!\n")

        attempts_left--
        if(attempts_left>0) goto ask_guess
        ; more efficient:  if_nz goto ask_guess

        ; game over.
        c64scr.print_string("\nToo bad! It was: ")
        c64scr.print_byte_decimal(secretnumber)
        c64.CHROUT('\n')

goodbye:
        c64scr.print_string("\nThanks for playing. Bye!\n")
        return
    }
}
