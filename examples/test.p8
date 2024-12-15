main {
    sub start() {
        goto $3000
        goto labeltje

        goto cx16.r0

        goto cx16.r0+cx16.r1

        if cx16.r0==0
            goto cx16.r0+cx16.r1

        if cx16.r0>2000
            goto cx16.r0+cx16.r1

labeltje:
    }
}
