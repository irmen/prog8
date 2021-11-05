%import textio
%zeropage basicsafe

main {
  sub start() {
    uword xx=$2000
    ubyte yy=30
    ubyte zz=9
    ; sys.memset(xx+200, yy*2, zz+yy)

    @($d020) = (xx+(yy*zz)) as ubyte
  }
}
