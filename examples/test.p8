%import textio

main {
    sub start() {
        cx16.r0 = 2
        when cx16.r0 {
            1-> {
                ;nothing
            }
            2 -> {
                txt.print("two")
            }
            0-> {
                ;nothing
            }
        }
    }
}
