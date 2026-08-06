%import textio
%import coroutines
%zeropage basicsafe

main {
    sub start() {
        void coroutines.add(task1, 0)
        void coroutines.add(task2, 0)
        void coroutines.add(task3, 0)
        txt.print("starting 3 tasks...\n")
        coroutines.run(supervisor)
        txt.print("\ndone.\n")
    }

    ubyte @shared count

    sub supervisor() -> bool {
        count++
        if count==200 {
            coroutines.killall()
            return false
        }
        return true
    }

    sub task1() {
        repeat {
            txt.chrout('a')
            void coroutines.yield()
        }
    }

    sub task2() {
        repeat {
            txt.chrout('b')
            void coroutines.yield()
        }
    }

    sub task3() {
        repeat {
            txt.chrout('c')
            void coroutines.yield()
        }
    }
}
