
; Cooperative multitasking / Coroutines
; Uses stack return address juggling to cycle between the different tasks when they call yield().

%import textio


main {
    sub start() {
        txt.print("cooperative multitasking / coroutines")
        coroutines.start()
    }
}

coroutines {
    uword[] tasklist = [&task1, &task2, &task3, &task4, &vsynctask]

    sub start() {
        goto tasklist[0]
    }

    uword[len(tasklist)] returnaddresses
    ubyte active_task

    sub yield() {
        ; store the return address of the yielding task,
        ; and continue with the next one instead (round-robin)
        returnaddresses[active_task] = sys.popw()
        active_task++
        if active_task==len(returnaddresses)
            active_task=0
        cx16.r0 = returnaddresses[active_task]
        if cx16.r0==0 {
            ; fetch start address of next task.
            ; address on the stack must be pushed in reverse byte order
            ; also, subtract 1 from the start address because JSR pushes returnaddress minus 1
            cx16.r0 = tasklist[active_task]-1
            sys.push(cx16.r0H)
            sys.push(cx16.r0L)
        } else
            sys.pushw(cx16.r0)

        ; returning from yield then continues with the next coroutine
    }

    sub task1() {
        const ubyte x = 5
        ubyte y
        repeat {
            for y in 10 to 24 {
                txt.setchr(x, y-1, sc:' ')
                txt.setchr(x, y, sc:'1')
                yield()
            }
            for y in 24 downto 10 {
                txt.setchr(x, y+1, sc:' ')
                txt.setchr(x, y, sc:'1')
                yield()
            }
        }
        ; need infinite loop
    }

    sub task2() {
        const ubyte x = 10
        ubyte y
        repeat {
            for y in 5 to 18 {
                txt.setchr(x, y-1, sc:' ')
                txt.setchr(x, y, sc:'2')
                yield()
            }
            for y in 18 downto 5 {
                txt.setchr(x, y+1, sc:' ')
                txt.setchr(x, y, sc:'2')
                yield()
            }
        }
        ; need infinite loop
    }

    sub task3() {
        ubyte x
        const ubyte y = 10
        repeat {
            for x in 14 to 38 {
                txt.setchr(x-1, y, sc:' ')
                txt.setchr(x, y, sc:'3')
                yield()
            }
            for x in 38 downto 14 {
                txt.setchr(x+1, y, sc:' ')
                txt.setchr(x, y, sc:'3')
                yield()
            }
        }
        ; need infinite loop
    }

    sub task4() {
        ubyte x
        const ubyte y = 14
        repeat {
            for x in 15 to 30 {
                txt.setchr(x-1, y, sc:' ')
                txt.setchr(x, y, sc:'4')
                yield()
            }
            for x in 30 downto 15 {
                txt.setchr(x+1, y, sc:' ')
                txt.setchr(x, y, sc:'4')
                yield()
            }
        }
        ; need infinite loop
    }

    sub vsynctask() {
        repeat {
            sys.waitvsync()
            sys.waitvsync()
            yield()
        }
        ; need infinite loop
    }
}


