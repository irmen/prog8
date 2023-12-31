%import textio
%zeropage basicsafe

; Note: this program can be compiled for multiple target systems.

main {
    sub start() {
        cx16.r0L=1
        while cx16.r0L < 10 and cx16.r0L>0 {
            cx16.r0L++
        }
    }
}
