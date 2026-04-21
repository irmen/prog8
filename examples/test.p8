%import monogfx
%import verafx

main {
    sub start() {
        monogfx.lores()
        monogfx.circle(160, 60, 59, true)

        verafx.clear(0, 0, 255, 320*10/8/4)
        verafx.copy(0,0, 0, 320*120/8, 320*120/8/4)

        repeat {}
    }
}
