main {
    sub start() {
        cx16.r0 = $aabb
        cx16.r1 = $1122

        goto cx16.r1+256
    }
}
