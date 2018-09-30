%option enable_floats

~ main {
sub start() -> () {
    str msg1 = "abc"
    str msg2 = "abc"
    str msg3 = "abc123"
    str_p msg4 = "abc"
    byte[5] array1 = 0
    byte[5] array2 = 222
    byte[6] array3 = [1,2,3,4,5,66]
    word[5] array4 = 333
    word[5] array5 = [1,2,3,4,59999]
    word[5] array6 = [1,2,3,4,5]
    word[5] array7 = [1,2,3,4,999+22/33]
    byte[2,3] matrix1 = [1,2, 3,4, 5,6]
    byte[2,3] matrix2 = [1,2, 3,4, 5,6]
    byte[2,3] matrix3 = [11,22, 33,44, 55,66]
    byte num1 = sin(2.0)
    word num2 = rndw()
    float num3 = 98.555

    num1=rnd()

    num1=thing()
    return


    sub thing() -> (X) {
        return 99
    }
}
}
