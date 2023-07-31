%import textio

main {
  ubyte[2] data = [100, 100]
  uword dRef = &data

  sub start() {
    dRef[0]--
    dRef[1]++

    cx16.r0L = 0
    cx16.r1L = 1
    dRef[cx16.r0L]--
    dRef[cx16.r1L]++

    txt.print_ub(data[0])
    txt.spc()
    txt.print_ub(data[1])
  }
}
