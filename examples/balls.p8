%import textio
%import test_stack

%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

main {

    sub start() {
        str  input = ".........."
        ubyte ballCount
        ubyte[255] BX
        ubyte[255] BY
        ubyte[255] BC
        ubyte[255] DX
        ubyte[255] DY

        txt.print("number of balls (1-255)? ")
        void txt.input_chars(input)
        ballCount = conv.str2ubyte(input)
        txt.fill_screen(81, 0)

        ; Setup Starting Ball Positions
        ubyte lp
        for lp in 0 to ballCount-1 {
            BX[lp] = rnd() % txt.DEFAULT_WIDTH
            BY[lp] = rnd() % txt.DEFAULT_HEIGHT
            BC[lp] = rnd() & 15
            DX[lp] = rnd() & 1
            DY[lp] = rnd() & 1
        }

        ; start clock
        c64.SETTIM(0,0,0)

        ; display balls
        uword frame
        for frame in 0 to 999 {
            ; Loop though all balls clearing current spot and setting new spot
            for lp in 0 to ballCount-1 {

                ; Clear existing Location the ball is at
                txt.setclr(BX[lp], BY[lp], 0)

                if DX[lp] == 0 {
                    if (BX[lp] == 0)
                    {
                        DX[lp] = 1
                    } else {
                        BX[lp]=BX[lp]-1
                    }
                } else if DX[lp] == 1 {
                    if (BX[lp] == txt.DEFAULT_WIDTH-1)
                    {
                        BX[lp] = txt.DEFAULT_WIDTH-2
                        DX[lp] = 0
                    } else {
                        BX[lp]=BX[lp]+1
                    }
                }
                if DY[lp] == 0 {
                    if (BY[lp] == 0)
                    {
                        DY[lp] = 1
                    } else {
                        BY[lp]=BY[lp]-1
                    }
                } else if DY[lp] == 1 {
                    if (BY[lp] == txt.DEFAULT_HEIGHT-1)
                    {
                        BY[lp] = txt.DEFAULT_HEIGHT-2
                        DY[lp] = 0
                    } else {
                        BY[lp]=BY[lp]+1
                    }
                }

                ; Put the new ball possition
                txt.setclr(BX[lp], BY[lp], BC[lp])
            }

            ;txt.plot(0,0)
            ;txt.print_uw(frame)
        }

        uword jiffies = c64.RDTIM16()
        txt.print("\nbenchmark: ")
        txt.print_uw(jiffies)
        txt.print(" jiffies for 1000 frames.\n")

        ; test_stack.test()
    }
}
