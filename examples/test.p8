%import textio
%import string
%zeropage basicsafe

main {

    sub start() {
        str output_filename = "?????????\x00????????????"
        void string.copy(".prg", &output_filename + string.length(output_filename))
        txt.print(output_filename)
    }
}

