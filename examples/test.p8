%import textio

main {
    const ubyte ATTR_FALLING=$04
    const ubyte ATTRF_EATABLE=$80

    sub start() {
        void test(10,20)
    }

    ubyte[10] attributes

  sub test(ubyte tattr, ubyte tobject) -> bool {
    return tattr&ATTR_FALLING==0 and attributes[tobject]&ATTRF_EATABLE
  }
}
