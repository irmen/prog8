%option enable_floats

~ main {
sub start() {
    byte bvar
    word wvar
    float fvar
    str svar = "svar"
    str_p spvar = "spvar"
    str_s ssvar = "ssvar"
    str_ps spsvar = "spsvar"
    byte[2,3] matrixvar
    byte[5] barrayvar
    word[5] warrayvar


    set_carry()
    clear_carry()
    set_irqd()
    clear_irqd()

    rol(bvar)
    rol(wvar)
    rol(fvar)
    rol(svar)
    rol(spvar)
    rol(ssvar)
    rol(spsvar)
    rol(matrixvar)
    rol(barrayvar)
    rol(warrayvar)

    rol2(bvar)
    rol2(wvar)
    rol2(fvar)
    rol2(svar)
    rol2(spvar)
    rol2(ssvar)
    rol2(spsvar)
    rol2(matrixvar)
    rol2(barrayvar)
    rol2(warrayvar)

    ror(bvar)
    ror(wvar)
    ror(fvar)
    ror(svar)
    ror(spvar)
    ror(ssvar)
    ror(spsvar)
    ror(matrixvar)
    ror(barrayvar)
    ror(warrayvar)

    ror2(bvar)
    ror2(wvar)
    ror2(fvar)
    ror2(svar)
    ror2(spvar)
    ror2(ssvar)
    ror2(spsvar)
    ror2(matrixvar)
    ror2(barrayvar)
    ror2(warrayvar)

    lsl(bvar)
    lsl(wvar)
    lsl(fvar)
    lsl(svar)
    lsl(spvar)
    lsl(ssvar)
    lsl(spsvar)
    lsl(matrixvar)
    lsl(barrayvar)
    lsl(warrayvar)

    lsr(bvar)
    lsr(wvar)
    lsr(fvar)
    lsr(svar)
    lsr(spvar)
    lsr(ssvar)
    lsr(spsvar)
    lsr(matrixvar)
    lsr(barrayvar)
    lsr(warrayvar)

}
}
