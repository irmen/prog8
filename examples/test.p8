%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        cx16.reset_system()
        repeat {
            for cx16.r0L in 0 to 255 {
               cx16.set_led_brightness(cx16.r0L)
               delay()
            }
            for cx16.r0L in 255 downto 0 {
               cx16.set_led_brightness(cx16.r0L)
               delay()
            }
        }
    }

    sub delay() {
        repeat 2000 {
            %asm {{
                nop
            }}
        }
    }
}
