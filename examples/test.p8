%import textio
%zeropage basicsafe

main {
    sub start() {
        word llw = 300
        cx16.r0s = 9 * 2 * 10 * llw
        cx16.r1s = llw * 9 * 2 * 10
        cx16.r3s = llw / 30 / 3
        cx16.r4s = llw / 2 * 10
        cx16.r5s = llw * 90 / 5     ; not optimized because of loss of integer division precision
    }
}
