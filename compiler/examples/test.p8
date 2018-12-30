%import c64utils

~ main {

    sub start()  {

        uword vic = $d000
        const uword cvic = $d000

        @(cvic+$20) = 7
        @(cvic+$21) = @(cvic+$20)

        @(vic+$20) = 5
        @(vic+$21) = @(vic+$20)

    }
}
