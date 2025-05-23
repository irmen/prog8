%import math
%import textio

main {
    sub start() {
        repeat {
            sys.waitvsync()
            sys.waitvsync()
            txt.cls()
            txt.print_ubbin(cx16.joysticks_detect(), true)
            txt.nl()
            txt.print_uwbin(cx16.joysticks_getall(true),true)
;
;            ubyte joystick
;            for joystick in 0 to 4 {
;                bool present
;                uword joy
;                joy,present = cx16.joystick_get(joystick)
;                txt.print_ub(joystick)
;                txt.print(": ")
;                txt.print_bool(present)
;                txt.spc()
;                txt.print_uwbin(joy, true)
;                txt.nl()
;            }
        }
    }
}
