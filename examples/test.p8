%import textio
%zeropage basicsafe

main
{
    sub start()
    {
        ubyte res
        ubyte val=3
        when val*10 {
            1 -> res=1
            10 -> res=10
            20 -> res=20
            30 -> res=30
            40 -> res=40
            else -> res=0
        }

        txt.print_ub(res)   ; 30
        txt.nl()

        when val {
            5,7,9,1,3 -> res=100
            2,4,6,8 -> res=200
            else -> res=0
        }

        txt.print_ub(res)   ; 100
        ; 102 instructions, 31 registers, 122 steps
    }
}
