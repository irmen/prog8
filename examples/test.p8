%import textio
%import utility

main {
    sub start() {
        if sys.UtilityBase==0 {
            txt.print("no utility.library\n")
            return
        }

        txt.print_ulhex(utility.GetUniqueID(), true)
        txt.nl()
        txt.print_ulhex(utility.GetUniqueID(), true)
        txt.nl()
        txt.print_ulhex(utility.GetUniqueID(), true)
        txt.nl()
        txt.print_ulhex(utility.GetUniqueID(), true)
        txt.nl()
    }
}
