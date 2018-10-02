
~ main {


sub start() {

    byte[10] barray1
    byte[10] barray2 = 11
    byte[10] barray3 = [1,2,3,4,5,6,7,8,9,255]

    word[10] warray1
    word[10] warray2 = 112233
    word[10] warray3 = [1,2,3,4,5,6,7,8,9, 65535]

    byte[4,5] mvar1
    byte[4,5] mvar2 = 22
    byte[2,3] mvar3 = [1,2,3,4,5,6]

    float[3]  farray1
    float[3]  farray2 = 33.44
    float[3]  farray2b = 33
    float[3]  farray3 = [1,2,3]
    float[3]  farray4 = [1,2,35566]
    float[3]  farray5 = [1,2.22334,3.1415]


    byte i
    word w

    ;warray3[1] = warray3[1] + 1
    warray3[1] += 1
    ;warray3[1] ++



;    for i in 0 to 2 {
;        _vm_write_num(farray5[i])
;        _vm_write_char('\n')
;    }

;    for w in [1,2,3777] {          ;@todo loop over array literal
;        _vm_write_num(w)
;        _vm_write_char('\n')
;    }
;
;    for i in barray3 {          ; @todo loop over symbol
;        _vm_write_num(i)
;        _vm_write_char('\n')
;    }
;
;    for i in "hello" {          ; @todo loop over string
;        _vm_write_num(i)
;        _vm_write_char('\n')
;    }
;
;        for w in "hello" {          ; @todo loop over string
;            _vm_write_num(w)
;            _vm_write_char('\n')
;        }

    return

}
}
