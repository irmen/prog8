%import textio
%import diskio
%zeropage basicsafe

main {
    sub start() {
        cbm.SETNAM(7, "0:blerp")
        cbm.SETLFS(12, 8, 0)
        void cbm.OPEN()          ; open 12,8,0,"$"
        cbm.CLOSE(12)

        txt.print(diskio.status())

;
;        void cbm.CHKIN(12)
;
;        while cbm.READST()==0 {
;            cx16.r0L = cbm.CHRIN()
;            txt.chrout(cx16.r0L)
;        }
;
;        cbm.CLOSE(12)
    }
}
