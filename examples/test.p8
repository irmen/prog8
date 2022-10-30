%import textio
%zeropage basicsafe

main {
  sub start() {
    cx16.r0 = 42
    goto foobar
my_label:
    txt.print_uwhex(cx16.r0, true)
    txt.spc()
    cx16.r0--
    if cx16.r0
        goto my_label
    sys.exit(0)

foobar:
    txt.print("yeooo\n")
    goto my_label
  }
}
