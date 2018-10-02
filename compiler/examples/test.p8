%option enable_floats



~ main {

asmsub	MOVFM		(mflpt: word @ AY) -> clobbers(A,Y) -> ()	= $bba2		; load mflpt value from memory  in A/Y into fac1
asmsub	FREADMEM	() -> clobbers(A,Y) -> ()			= $bba6		; load mflpt value from memory  in $22/$23 into fac1
asmsub	CONUPK		(mflpt: word @ AY) -> clobbers(A,Y) -> ()	= $ba8c		; load mflpt value from memory  in A/Y into fac2
asmsub	FAREADMEM	() -> clobbers(A,Y) -> ()			= $ba90		; load mflpt value from memory  in $22/$23 into fac2
asmsub	MOVFA		() -> clobbers(A,X) -> ()			= $bbfc		; copy fac2 to fac1
asmsub	MOVAF		() -> clobbers(A,X) -> ()			= $bc0c		; copy fac1 to fac2  (rounded)
asmsub	MOVEF		() -> clobbers(A,X) -> ()			= $bc0f		; copy fac1 to fac2
asmsub	FTOMEMXY	(mflpt: word @ XY) -> clobbers(A,Y) -> ()	= $bbd4		; store fac1 to memory  X/Y as 5-byte mflpt


sub start() {

    byte bvar = 128
    word wvar = 128
    float fvar = 128

    bvar = 1
    bvar = 2.0
    ;bvar = 2.w     ; @todo don't crash
    wvar = 1        ; @todo optimize byte literal to word literal
    wvar = 2.w
    wvar = 2.0
    wvar = bvar
    fvar = 1        ; @todo optimize byte literal to float literal
    fvar = 2.w      ; @todo optimize word literal to float literal
    fvar = 22.33
    fvar = bvar
    fvar = wvar

    MOVAF()

    return

}
}
