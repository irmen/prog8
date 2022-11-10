%import textio
%import psg
%zeropage basicsafe

main {

    sub start() {
        ubyte @shared variable

        sub nested() {
            ubyte @shared variable2

            variable2 = 33
            nested()

            sub nested() {
                ubyte @shared variable3

                variable3 = 33
            }
        }

        nested()
        explosion()
    }

    sub explosion() {
        ; this subroutine is not used but it is an example of how to make a sound effect using the psg library!
        psg.silent()
        psg.voice(0, psg.LEFT, 63, psg.NOISE, 0)
        psg.voice(1, psg.RIGHT, 63, psg.NOISE, 0)
        psg.freq(0, 1000)
        psg.freq(1, 2000)
    }
}
