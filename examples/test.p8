%import textio
%zeropage basicsafe

main
{
    sub start()
    {
        byte zc = -55

        when zc*3 {
            123 -> zc++
            124 -> zc++
            125 -> zc++
            121 -> zc++
            120 -> zc++
            else -> zc++
        }
    }
}
