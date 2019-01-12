%import c64utils
%import c64flt

~ main {

    sub start()  {

        uword[4] uwa = 5
        ubyte[4] uba = 5
        word[4] wa = 5
        byte[4] ba = 5
        float[4] fa = 5.123
        str naam = "irmen"
        float ff = 3.4444

        uword addr

        addr = naam
        addr = uwa
        addr = fa

        pairAX(naam)
        pairAX("hello")
        pairAX("hello2")
        pairAX("hello2")
        pairAX("hello2")
        pairAY("hello2")
        pairAY("hello2")
        pairXY("hello2")
        pairXY("hello2")
        pairAX(uwa)
        pairAX(fa)
        pairAY(naam)
        pairAY(uwa)
        pairAY(fa)
        pairXY(naam)
        pairXY(uwa)
        pairXY(fa)

    }

    asmsub pairAX(uword address @ AX) -> clobbers() -> () {
    }
    asmsub pairAY(uword address @ AY) -> clobbers() -> () {
    }
    asmsub pairXY(uword address @ XY) -> clobbers() -> () {
    }
}
