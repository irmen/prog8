main  {
    sub start() {
        func()
    }

    sub func() {
        if cx16.r0<10 or cx16.r0>319 {
            cx16.r1++
        }
    }
}
