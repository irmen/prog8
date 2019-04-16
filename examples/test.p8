%zeropage basicsafe

~ main {

    sub start() {

        %asminclude "primes.p8", "derp"
        %asmbinary "primes.p8", 10, 20
    }
}
