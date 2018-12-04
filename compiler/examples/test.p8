%output raw
%launcher none
%import c64utils

~ main {
    memory ubyte[40*25] Screen = $0400

    sub start()  {
        A, Y = c64flt.GETADRAY()
    }
}
