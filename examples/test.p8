%import textio
%zeropage dontuse

main {
    sub start() {
        uword pointer = $4000
        ubyte index = $e3
        @($40e2) = 69
;        cx16.r0L = pointer[index-1]

        ;cx16.r0L=69
        ;pointer[16] = cx16.r0L
        ubyte targetindex=16
        pointer[targetindex] = pointer[index-1]
        pointer[16] = pointer[index-1]
        txt.print_ub(@($4010))      ; expected: 69
	}
}
