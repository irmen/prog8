%import strings
%import textio
%zeropage basicsafe

main {
    sub start() {
        txt.lowercase()

        str message = iso:"The Quick Brown Fox !@#$%^&*()  {_}  12345  /'`|"

        txt.iso2petscii_str(&message)
        txt.print(message)
        txt.nl()
    }
}
