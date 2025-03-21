%import textio
%zeropage basicsafe
%option no_sysinit, romable

main {

    sub start()  {
        uword @shared @nozp ptr

        cx16.r0L = @(ptr)

        @(ptr)++

        @(ptr)+=10
    }
}
