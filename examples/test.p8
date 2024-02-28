%import string
%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {

        if not string.isdigit(cx16.r0L)
            goto done

        cx16.r0L++
done:
    }
}

