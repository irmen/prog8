; Cooperative Multitasking example.
; Can be compiled for different targets (except virtual).

%import coroutines
%import textio

%zeropage basicsafe


main {
    sub start() {
        txt.print("cooperative multitasking / coroutines\n\n")
        txt.print("here are couple of routines that each\nrun a few loops bouncing a digit around.\n")

        coroutines.killall()
        void coroutines.add(&task1)
        void coroutines.add(&task2)
        void coroutines.add(&task3)
        void coroutines.add(&task4)
        void coroutines.add(&delaytask)
        coroutines.run()

        txt.print("we're all done!\n")
    }

    sub task1() {
        const ubyte x = 5
        ubyte y
        repeat 3 {
            for y in 10 to 24 {
                txt.setchr(x, y-1, sc:' ')
                txt.setchr(x, y, sc:'1')
                coroutines.yield()
            }
            for y in 24 downto 10 {
                txt.setchr(x, y+1, sc:' ')
                txt.setchr(x, y, sc:'1')
                coroutines.yield()
            }
        }
        txt.setchr(x, 10, sc:' ')
    }

    sub task2() {
        const ubyte x = 10
        ubyte y
        repeat 2 {
            for y in 5 to 18 {
                txt.setchr(x, y-1, sc:' ')
                txt.setchr(x, y, sc:'2')
                coroutines.yield()
            }
            for y in 18 downto 5 {
                txt.setchr(x, y+1, sc:' ')
                txt.setchr(x, y, sc:'2')
                coroutines.yield()
            }
        }
        txt.setchr(x, 5, sc:' ')

        ; add a new task dynamically
        void coroutines.add(&task5)
    }

    sub task3() {
        ubyte x
        const ubyte y = 10
        repeat 4 {
            for x in 14 to 38 {
                txt.setchr(x-1, y, sc:' ')
                txt.setchr(x, y, sc:'3')
                coroutines.yield()
            }
            for x in 38 downto 14 {
                txt.setchr(x+1, y, sc:' ')
                txt.setchr(x, y, sc:'3')
                coroutines.yield()
            }
        }
        txt.setchr(14, y, sc:' ')
    }

    sub task4() {
        ubyte x
        const ubyte y = 14
        repeat 4 {
            for x in 15 to 30 {
                txt.setchr(x-1, y, sc:' ')
                txt.setchr(x, y, sc:'4')
                coroutines.yield()
            }
            for x in 30 downto 15 {
                txt.setchr(x+1, y, sc:' ')
                txt.setchr(x, y, sc:'4')
                coroutines.yield()
            }
        }
        txt.setchr(15, y, sc:' ')
    }

    sub task5() {
        ubyte x
        const ubyte y = 16
        repeat 4 {
            for x in 15 to 30 {
                txt.setchr(x-1, y, sc:' ')
                txt.setchr(x, y, sc:'5')
                coroutines.yield()
            }
            for x in 30 downto 15 {
                txt.setchr(x+1, y, sc:' ')
                txt.setchr(x, y, sc:'5')
                coroutines.yield()
            }
        }
        txt.setchr(15, y, sc:' ')
    }

    sub delaytask() {
        repeat 200 {
            sys.waitvsync()
            sys.waitvsync()
            coroutines.yield()
        }
    }
}
