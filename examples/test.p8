%import palette
%import test_stack
%zeropage basicsafe

main {

    sub start() {
        ; TODO inline a subroutine that only contains a direct call to another subroutine
        palette.set_all_black()
        palette.set_all_white()
    }
}
