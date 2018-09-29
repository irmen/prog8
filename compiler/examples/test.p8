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
    byte num1 = 99
    word num2 = 12345
    float num3 = 98.555


    num1 = max(msg3)
    _vm_write_str(num1)
    _vm_write_char($8d)
    num1 = min(msg3)
    _vm_write_str(num1)
    _vm_write_char($8d)

    num3 = avg(array3)
    _vm_write_str(num3)
    _vm_write_char($8d)
    num1 = all(array3)
    _vm_write_str(num1)
    _vm_write_char($8d)
    num1 = any(array3)
    _vm_write_str(num1)
    _vm_write_char($8d)

    num1 = max(array3)
    _vm_write_str(num1)
    _vm_write_char($8d)
    num1 = min(array3)
    _vm_write_str(num1)
    _vm_write_char($8d)
    num2 = max(array5)
    _vm_write_str(num2)
    _vm_write_char($8d)
    num2 = min(array5)
    _vm_write_str(num2)
    _vm_write_char($8d)
    num2 = sum(array3)
    _vm_write_str(num2)
    _vm_write_char($8d)
    num2 = sum(array5)
    _vm_write_str(num2)
    _vm_write_char($8d)

    num3 = avg(msg3)
    _vm_write_str(num3)
    _vm_write_char($8d)
    num2 = sum(msg3)
    _vm_write_str(num2)
    _vm_write_char($8d)
    num1 = all(msg3)
    _vm_write_str(num1)
    _vm_write_char($8d)
    num1 = any(msg3)
    _vm_write_str(num1)
    _vm_write_char($8d)

}
}
