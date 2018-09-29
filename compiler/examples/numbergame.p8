%output prg

~ main {
    sub start() -> () {
        str   name    = "?" * 20
        str   guess   = "?" * 20
        byte  secretnumber = 0
        byte  attempts_left = 10

        ; greeting
        _vm_write_str("Enter your name: ")
        ; _vm_input_str(name)
        _vm_write_char($8d)
        _vm_write_char($8d)
        _vm_write_str("Hello, ")
        _vm_write_str(name)
        _vm_write_char($2e)
        _vm_write_char($8d)

        return

;        ; create a secret random number from 1-100
;        c64.RNDA(0)             ; fac = rnd(0)
;        c64.MUL10()             ; fac *= 10
;        c64.MUL10()             ; .. and now *100
;        c64.FADDH()             ; add 0.5..
;        c64.FADDH()             ;   and again, so +1 total
;        AY = c64flt.GETADRAY()
;        secretnumber = A
;        ;A=math.randbyte()
;        ;A+=c64.RASTER
;        ;A-=c64.TIME_LO
;        ;X,secretnumber=math.divmod_bytes(A, 99)
;
;        c64scr.print_string("I am thinking of a number from 1 to 100!You'll have to guess it!\n")
;
;ask_guess:
;        c64scr.print_string("\nYou have ")
;        c64scr.print_byte_decimal(attempts_left)
;        c64scr.print_string(" guess")
;        if(attempts_left>0) c64scr.print_string("es")
;
;        c64scr.print_string(" left.\nWhat is your next guess? ")
;        Y = c64scr.input_chars(guess)
;        c64.CHROUT("\n")
;        freadstr_arg = guess
;        c64.FREADSTR(A)
;        AY = c64flt.GETADRAY()
;        if(A==secretnumber) {
;            c64scr.print_string("\nThat's my number, impressive!\n")
;            goto goodbye
;        }
;        c64scr.print_string("That is too ")
;        if(A > secretnumber)
;            c64scr.print_string("low!\n")
;        else
;            c64scr.print_string("high!\n")
;
;        attempts_left--
;        if(attempts_left>0) goto ask_guess
;        ; more efficient:  if_nz goto ask_guess
;
;        ; game over.
;        c64scr.print_string("\nToo bad! It was: ")
;        c64scr.print_byte_decimal(secretnumber)
;        c64.CHROUT("\n")
;
;goodbye:
;        c64scr.print_string("\nThanks for playing. Bye!\n")
;        return
    }
}
