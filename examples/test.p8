%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
       str foo = "foo"
       str bar = "bar"
       bool flag = true
       uword @shared foobar = if flag foo else bar
    }
}
