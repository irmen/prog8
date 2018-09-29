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
    str  message = "Calculating Mandelbrot Fractal..."
    _vm_gfx_text(5, 5, 7, message)
    _vm_gfx_text(5, 5, 7, "Calculating Mandelbrot Fractal...")

    A= len("abcdef")

    A= len([4,5,99/X])
    A= max([4,5,99])
    A= min([4,5,99])
    A= avg([4,5,99])
    A= sum([4,5,99])
    A= any([4,5,99])
    A= all([4,5,99])

    A= len(msg3)

    float xx

    A= len(array3)
    A= max(array3)
    A= min(array3)
    xx= avg(array3)
    A= sum(array3)
    A= any(array3)
    A= all(array3)

}
}
