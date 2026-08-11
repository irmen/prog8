%zeropage basicsafe
%encoding iso
%import textio

main {
    sub start() {
        long asc_sum = 0
        long @shared asc_step = 10000
        long i
        for i in 0 to 50000 step asc_step {
            asc_sum += i
        }
        if asc_sum == 150000
            txt.print("pass long asc\n")
        else
            txt.print("fail long asc\n")

        long desc_sum = 0
        long @shared desc_step = -10000
        for i in 50000 downto 0 step desc_step {
            desc_sum += i
        }
        if desc_sum == 150000
            txt.print("pass long desc\n")
        else
            txt.print("fail long desc\n")

        sys.exit(0)
    }
}
