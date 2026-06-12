; Example of using a banking subroutine with extsub @bank.
; This example uses the 'virtual' target to demonstrate the concept.
; On the virtual target, banks are simulated but for the purpose of
; this example, we just show how the banking routine is called.

%import textio
%zeropage basicsafe

main {
    ; We define a banking subroutine that will be called before the extsub.
    ; It must be parameterless and return a ubyte.
    sub get_bank() -> ubyte {
        txt.print(" (banking routine called) ")
        return 4        ; x16 ram bank 4 = BASIC
    }

    ; Define an external subroutine that uses our banking routine.
    ; In this example, we point it to a dummy address on the virtual machine.
    extsub @bank get_bank $ffd2 = banked_routine(ubyte char @A)

    sub start() {
        txt.print("calling banked_routine...\n")
        banked_routine('*')
        txt.print("\ndone.\n")
    }
}
