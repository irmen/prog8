%import textio
%import math
%zeropage basicsafe

; Note: this program can be compiled for multiple target systems.

main {

    sub start() {
        str  input = ".........."
        ubyte ballCount
        ubyte[255] BX
        ubyte[255] BY
        ubyte[255] BC
        bool[255] DX
        bool[255] DY

        txt.print("number of balls (1-255)? ")
        void txt.input_chars(input)
        ballCount = conv.str2ubyte(input)
        txt.fill_screen(81, 0)

        ; Setup Starting Ball Positions
        ubyte lp
        for lp in 0 to ballCount-1 {
            BX[lp] = math.rnd() % txt.DEFAULT_WIDTH
            BY[lp] = math.rnd() % txt.DEFAULT_HEIGHT
            BC[lp] = math.rnd() & 15
            DX[lp] = math.rnd() & 1 == 1
            DY[lp] = math.rnd() & 1 == 1
        }

        ; display balls
        repeat {
            ; Loop though all balls clearing current spot and setting new spot
            for lp in 0 to ballCount-1 {

                ; Clear existing Location the ball is at
                txt.setclr(BX[lp], BY[lp], 0)

                if not DX[lp] {
                    if (BX[lp] == 0)
                        DX[lp] = true
                    else
                        BX[lp]=BX[lp]-1
                } else {
                    if (BX[lp] == txt.DEFAULT_WIDTH-1) {
                        BX[lp] = txt.DEFAULT_WIDTH-2
                        DX[lp] = false
                    } else {
                        BX[lp]=BX[lp]+1
                    }
                }
                if not DY[lp] {
                    if (BY[lp] == 0)
                        DY[lp] = true
                    else
                        BY[lp]=BY[lp]-1
                } else {
                    if (BY[lp] == txt.DEFAULT_HEIGHT-1) {
                        BY[lp] = txt.DEFAULT_HEIGHT-2
                        DY[lp] = false
                    } else {
                        BY[lp]=BY[lp]+1
                    }
                }

                ; Put the new ball possition
                txt.setclr(BX[lp], BY[lp], BC[lp])
            }

            sys.waitvsync()
        }
    }
}
