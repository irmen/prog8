; Cooperative Multitasking example.
; Can be compiled for different targets (except virtual).

%import coroutines
%import textio

%zeropage basicsafe


main {
    sub start() {
        txt.print("cooperative multitasking / coroutines\n\n")
        txt.print("here are couple of routines that each\nrun a few loops bouncing a digit around.\n")
        txt.print("press any key to add a new counter task.")

        coroutines.killall()
        void coroutines.add(&task1, sc:'1')
        void coroutines.add(&task2, sc:'2')
        void coroutines.add(&task3, sc:'3')
        void coroutines.add(&task4, sc:'4')
        void coroutines.add(&keyhandler, 0)
        void coroutines.add(&delaytask, 0)
        coroutines.run(&supervisor)     ; can also use 0 if you don't need a supervisor routine
        ; the supervisor is called every time a task switch is about to occur - and you can control that somewhat.
        ; if that control is not needed you could also just add a "system" routine as a regular task,
        ; much like the keyhandler and delay tasks above.

        ; we will end up here if there are no more tasks to run (doesn't happen in this example)
        txt.print("we're all done!\n")
    }

    sub keyhandler() {
        repeat {
            ubyte key = cbm.GETIN2()
            if key!=0 {
                ubyte taskid = coroutines.add(&countertask, key)
                counters[taskid] = 222
            }
            void coroutines.yield()
        }
    }

    sub supervisor() -> bool {
        ; you can call coroutines.current() to get the id of the next task to run.
        ; (or just read the active_task variable)
        ; return true to execute that task, or false to skip it this time.
        ; or do something else....?
        return true
    }

    ubyte[coroutines.MAX_TASKS] counters

    sub countertask() {
        repeat {
            uword userdata = coroutines.yield()     ; yield and obtain our userdata
            ubyte tid = coroutines.current()        ; what task are we?
            counters[tid]--                         ; our counter is in the array, it cannot be kept in a local variable (shared state)

            txt.plot(15, 10 + tid)
            if counters[tid] == 0 {
                txt.print("   ")
                return              ; done, exit the task
            } else {
                txt.chrout(lsb(userdata))
                txt.chrout(':')
                txt.print_uw(counters[tid])
                txt.spc()
            }
        }
    }

    sub task1() {
        const ubyte x = 5
        ubyte y
        repeat 3 {
            for y in 10 to 24 {
                txt.setchr(x, y-1, sc:' ')
                txt.setchr(x, y, sc:'1')
                void coroutines.yield()
            }
            for y in 24 downto 10 {
                txt.setchr(x, y+1, sc:' ')
                txt.setchr(x, y, sc:'1')
                void coroutines.yield()
            }
        }
        txt.setchr(x, 10, sc:' ')
    }

    sub task2() {
        const ubyte x = 10
        ubyte y
        repeat 2 {
            for y in 9 to 22 {
                txt.setchr(x, y-1, sc:' ')
                txt.setchr(x, y, sc:'2')
                void coroutines.yield()
            }
            for y in 22 downto 9 {
                txt.setchr(x, y+1, sc:' ')
                txt.setchr(x, y, sc:'2')
                void coroutines.yield()
            }
        }
        txt.setchr(x, 9, sc:' ')

        ; add a new task dynamically
        void coroutines.add(&task5, 0)
    }

    sub task3() {
        ubyte x
        const ubyte y = 10
        repeat 4 {
            for x in 14 to 38 {
                txt.setchr(x-1, y, sc:' ')
                txt.setchr(x, y, sc:'3')
                void coroutines.yield()
            }
            for x in 38 downto 14 {
                txt.setchr(x+1, y, sc:' ')
                txt.setchr(x, y, sc:'3')
                void coroutines.yield()
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
                void coroutines.yield()
            }
            for x in 30 downto 15 {
                txt.setchr(x+1, y, sc:' ')
                txt.setchr(x, y, sc:'4')
                void coroutines.yield()
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
                void coroutines.yield()
            }
            for x in 30 downto 15 {
                txt.setchr(x+1, y, sc:' ')
                txt.setchr(x, y, sc:'5')
                void coroutines.yield()
            }
        }
        txt.setchr(15, y, sc:' ')
    }

    sub delaytask() {
        repeat 200 {
            sys.waitvsync()
            sys.waitvsync()
            void coroutines.yield()
        }
    }
}
