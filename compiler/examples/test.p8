%import c64utils

~ main  {

sub start() {

    memory ubyte[40] firstScreenLine = $0400

    for ubyte c in 10 to 30 {
        firstScreenLine[c] = c
    }
    return
}


}
