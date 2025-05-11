main {
    sub start() {
        when cx16.r1L {
            2 -> goto first
            3 -> goto second
            else -> goto other
        }

        first:
        second:
        other:
    }
}
