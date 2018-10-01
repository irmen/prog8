
~ main {

; sub	VECTOR   (dir: Pc, userptr: XY) -> (X, A?, Y?)	= $FF8D		; read/set I/O vector table


asmsub VECTOR (dir: byte @ A, userptr: word @ XY) -> clobbers(A,X,Y) -> (byte @ X)  = $ff8d
; asmsub VECTOR (dir: byte @ Pc, userptr: word @ XY) -> byte @ X, clobbers @ A, clobbers @ Y = $ff8d



sub start() {
    return

}
}
