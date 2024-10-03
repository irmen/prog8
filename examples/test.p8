; EXAMPLE external command source code

%launcher none
%option no_sysinit
%zeropage basicsafe
%encoding iso
%import textio
%address $4000


main $4030 {
    %option force_output

    sub start()  {
        txt.print("external command\n")
        sys.exit(0)
    }
}
