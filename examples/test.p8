
main
{
    sub start()
    {
        cx16.set_screen_mode(128)

        cx16.vaddr_autoincr(0,320*100+100,1,2)
        repeat 16 {
            cx16.VERA_DATA1 = 0
        }
        cx16.vaddr_autodecr(0,320*110+100,1,1)
        repeat 16 {
            cx16.VERA_DATA1 = 0
        }

        repeat {
        }
    }
}
