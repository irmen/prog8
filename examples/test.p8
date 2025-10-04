%import textio
%zeropage basicsafe

main {
    sub start() {
        &long ll = 5000

        ll = $9988776655

        txt.print_ubhex(@(5000), false)
        txt.print_ubhex(@(5001), false)
        txt.print_ubhex(@(5002), false)
        txt.print_ubhex(@(5003), false)
    }
}
