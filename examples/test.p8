%zeropage basicsafe
%option no_sysinit
%import textio

main {
    sub start() {
        txt.iso()
        
        ; Test split word array += with constant value 1
        uword[] arr1 = [100, 200, 300]
        arr1[0] += 1
        arr1[1] += 1
        arr1[2] += 1
        
        if arr1[0] == 101 and arr1[1] == 201 and arr1[2] == 301 {
            txt.print("PASS: += 1\n")
        } else {
            txt.print("FAIL: += 1 (expected 101,201,301 got ")
            txt.print_uw(arr1[0])
            txt.print(",")
            txt.print_uw(arr1[1])
            txt.print(",")
            txt.print_uw(arr1[2])
            txt.print(")\n")
        }
        
        ; Test split word array += with constant value > 1
        uword[] arr2 = [100, 200, 300]
        arr2[0] += 50
        arr2[1] += 100
        arr2[2] += 1000
        
        if arr2[0] == 150 and arr2[1] == 300 and arr2[2] == 1300 {
            txt.print("PASS: += const\n")
        } else {
            txt.print("FAIL: += const (expected 150,300,1300 got ")
            txt.print_uw(arr2[0])
            txt.print(",")
            txt.print_uw(arr2[1])
            txt.print(",")
            txt.print_uw(arr2[2])
            txt.print(")\n")
        }
        
        ; Test split word array += with variable
        uword[] arr3 = [100, 200, 300]
        uword addval = 25
        arr3[0] += addval
        arr3[1] += addval * 2
        arr3[2] += addval * 3
        
        if arr3[0] == 125 and arr3[1] == 250 and arr3[2] == 375 {
            txt.print("PASS: += var\n")
        } else {
            txt.print("FAIL: += var (expected 125,250,375 got ")
            txt.print_uw(arr3[0])
            txt.print(",")
            txt.print_uw(arr3[1])
            txt.print(",")
            txt.print_uw(arr3[2])
            txt.print(")\n")
        }
        
        ; Test split word array -= with constant value 1
        uword[] arr4 = [100, 200, 300]
        arr4[0] -= 1
        arr4[1] -= 1
        arr4[2] -= 1
        
        if arr4[0] == 99 and arr4[1] == 199 and arr4[2] == 299 {
            txt.print("PASS: -= 1\n")
        } else {
            txt.print("FAIL: -= 1 (expected 99,199,299 got ")
            txt.print_uw(arr4[0])
            txt.print(",")
            txt.print_uw(arr4[1])
            txt.print(",")
            txt.print_uw(arr4[2])
            txt.print(")\n")
        }
        
        ; Test split word array -= with constant value > 1
        uword[] arr5 = [100, 200, 300]
        arr5[0] -= 50
        arr5[1] -= 100
        arr5[2] -= 150
        
        if arr5[0] == 50 and arr5[1] == 100 and arr5[2] == 150 {
            txt.print("PASS: -= const\n")
        } else {
            txt.print("FAIL: -= const (expected 50,100,150 got ")
            txt.print_uw(arr5[0])
            txt.print(",")
            txt.print_uw(arr5[1])
            txt.print(",")
            txt.print_uw(arr5[2])
            txt.print(")\n")
        }
        
        ; Test split word array -= with variable
        uword[] arr6 = [100, 200, 300]
        uword subval = 25
        arr6[0] -= subval
        arr6[1] -= subval * 2
        arr6[2] -= subval * 3
        
        if arr6[0] == 75 and arr6[1] == 150 and arr6[2] == 225 {
            txt.print("PASS: -= var\n")
        } else {
            txt.print("FAIL: -= var (expected 75,150,225 got ")
            txt.print_uw(arr6[0])
            txt.print(",")
            txt.print_uw(arr6[1])
            txt.print(",")
            txt.print_uw(arr6[2])
            txt.print(")\n")
        }
        
        ; Test carry/borrow across byte boundary
        uword[] arr7 = [255, 256, 1]
        arr7[0] += 1  ; Should become 256 (carry from LSB to MSB)
        arr7[1] += 1  ; Should become 257
        arr7[2] += 65534  ; Should become 65535 (max uword)
        
        if arr7[0] == 256 and arr7[1] == 257 and arr7[2] == 65535 {
            txt.print("PASS: carry test\n")
        } else {
            txt.print("FAIL: carry test (expected 256,257,65535 got ")
            txt.print_uw(arr7[0])
            txt.print(",")
            txt.print_uw(arr7[1])
            txt.print(",")
            txt.print_uw(arr7[2])
            txt.print(")\n")
        }
        
        ; Test borrow across byte boundary
        uword[] arr8 = [256, 257, 65535]
        arr8[0] -= 1  ; Should become 255
        arr8[1] -= 1  ; Should become 256
        arr8[2] -= 1  ; Should become 65534

        if arr8[0] == 255 and arr8[1] == 256 and arr8[2] == 65534 {
            txt.print("PASS: borrow test\n")
        } else {
            txt.print("FAIL: borrow test (expected 255,256,65534 got ")
            txt.print_uw(arr8[0])
            txt.print(",")
            txt.print_uw(arr8[1])
            txt.print(",")
            txt.print_uw(arr8[2])
            txt.print(")\n")
        }

        ; ============================================================
        ; Non-split word array tests
        ; ============================================================

        ; Test non-split array += with constant index, non-constant value
        uword[] @nosplit arr9 = [100, 200, 300]
        uword addval9 = 50
        arr9[0] += addval9
        arr9[1] += addval9 * 2

        if arr9[0] == 150 and arr9[1] == 300 {
            txt.print("PASS: nosplit += const-idx, var\n")
        } else {
            txt.print("FAIL: nosplit += const-idx, var (expected 150,300 got ")
            txt.print_uw(arr9[0])
            txt.print(",")
            txt.print_uw(arr9[1])
            txt.print(")\n")
        }

        ; Test non-split array -= with constant index, non-constant value
        uword[] @nosplit arr10 = [500, 400, 300]
        uword subval10 = 50
        arr10[0] -= subval10
        arr10[1] -= subval10 * 2

        if arr10[0] == 450 and arr10[1] == 300 {
            txt.print("PASS: nosplit -= const-idx, var\n")
        } else {
            txt.print("FAIL: nosplit -= const-idx, var (expected 450,300 got ")
            txt.print_uw(arr10[0])
            txt.print(",")
            txt.print_uw(arr10[1])
            txt.print(")\n")
        }

        ; Test non-split array += with non-constant index, constant value
        uword[] @nosplit arr11 = [100, 200, 300]
        ubyte idx11 = 1
        arr11[idx11] += 100

        if arr11[0] == 100 and arr11[1] == 300 and arr11[2] == 300 {
            txt.print("PASS: nosplit += var-idx, const\n")
        } else {
            txt.print("FAIL: nosplit += var-idx, const (expected 100,300,300 got ")
            txt.print_uw(arr11[0])
            txt.print(",")
            txt.print_uw(arr11[1])
            txt.print(",")
            txt.print_uw(arr11[2])
            txt.print(")\n")
        }

        ; Test non-split array += with non-constant index, non-constant value
        uword[] @nosplit arr12 = [100, 200, 300]
        ubyte idx12 = 2
        uword val12 = 25
        arr12[idx12] += val12

        if arr12[0] == 100 and arr12[1] == 200 and arr12[2] == 325 {
            txt.print("PASS: nosplit += var-idx, var\n")
        } else {
            txt.print("FAIL: nosplit += var-idx, var (expected 100,200,325 got ")
            txt.print_uw(arr12[0])
            txt.print(",")
            txt.print_uw(arr12[1])
            txt.print(",")
            txt.print_uw(arr12[2])
            txt.print(")\n")
        }

        ; Test non-split array -= with non-constant index, constant value
        uword[] @nosplit arr13 = [500, 400, 300]
        ubyte idx13 = 0
        arr13[idx13] -= 200

        if arr13[0] == 300 and arr13[1] == 400 and arr13[2] == 300 {
            txt.print("PASS: nosplit -= var-idx, const\n")
        } else {
            txt.print("FAIL: nosplit -= var-idx, const (expected 300,400,300 got ")
            txt.print_uw(arr13[0])
            txt.print(",")
            txt.print_uw(arr13[1])
            txt.print(",")
            txt.print_uw(arr13[2])
            txt.print(")\n")
        }

        ; Test non-split array -= with non-constant index, non-constant value
        uword[] @nosplit arr14 = [500, 400, 300]
        ubyte idx14 = 1
        uword val14 = 150
        arr14[idx14] -= val14

        if arr14[0] == 500 and arr14[1] == 250 and arr14[2] == 300 {
            txt.print("PASS: nosplit -= var-idx, var\n")
        } else {
            txt.print("FAIL: nosplit -= var-idx, var (expected 500,250,300 got ")
            txt.print_uw(arr14[0])
            txt.print(",")
            txt.print_uw(arr14[1])
            txt.print(",")
            txt.print_uw(arr14[2])
            txt.print(")\n")
        }

        ; ============================================================
        ; Split word array *= tests
        ; ============================================================

        ; Test split word array *= with constant value 1 (no-op)
        uword[] arr15 = [100, 200, 300]
        arr15[0] *= 1
        arr15[1] *= 1
        arr15[2] *= 1

        if arr15[0] == 100 and arr15[1] == 200 and arr15[2] == 300 {
            txt.print("PASS: *= 1\n")
        } else {
            txt.print("FAIL: *= 1 (expected 100,200,300 got ")
            txt.print_uw(arr15[0])
            txt.print(",")
            txt.print_uw(arr15[1])
            txt.print(",")
            txt.print_uw(arr15[2])
            txt.print(")\n")
        }

        ; Test split word array *= with constant value > 1
        uword[] arr16 = [10, 20, 100]
        arr16[0] *= 5
        arr16[1] *= 10
        arr16[2] *= 600

        if arr16[0] == 50 and arr16[1] == 200 and arr16[2] == 60000 {
            txt.print("PASS: *= const\n")
        } else {
            txt.print("FAIL: *= const (expected 50,200,60000 got ")
            txt.print_uw(arr16[0])
            txt.print(",")
            txt.print_uw(arr16[1])
            txt.print(",")
            txt.print_uw(arr16[2])
            txt.print(")\n")
        }

        ; Test split word array *= with variable
        uword[] arr17 = [10, 20, 30]
        uword mulval = 7
        arr17[0] *= mulval
        arr17[1] *= mulval * 2
        arr17[2] *= 3

        if arr17[0] == 70 and arr17[1] == 280 and arr17[2] == 90 {
            txt.print("PASS: *= var\n")
        } else {
            txt.print("FAIL: *= var (expected 70,280,90 got ")
            txt.print_uw(arr17[0])
            txt.print(",")
            txt.print_uw(arr17[1])
            txt.print(",")
            txt.print_uw(arr17[2])
            txt.print(")\n")
        }

        ; Test *= overflow (upper bits should be truncated to 16 bits)
        uword[] arr18 = [500, 256, 65535]
        arr18[0] *= 200  ; 500 * 200 = 100000, truncates to 100000 % 65536 = 34464
        arr18[1] *= 300  ; 256 * 300 = 76800, truncates to 76800 % 65536 = 11264
        arr18[2] *= 2    ; 65535 * 2 = 131070, truncates to 131070 % 65536 = 65534

        if arr18[0] == 34464 and arr18[1] == 11264 and arr18[2] == 65534 {
            txt.print("PASS: *= overflow\n")
        } else {
            txt.print("FAIL: *= overflow (expected 34464,11264,65534 got ")
            txt.print_uw(arr18[0])
            txt.print(",")
            txt.print_uw(arr18[1])
            txt.print(",")
            txt.print_uw(arr18[2])
            txt.print(")\n")
        }

        ; ============================================================
        ; Bitwise AND tests
        ; ============================================================

        ; Test split word array &= with constant value
        uword[] arr19 = [$FFFF, $F0F0, $FF00]
        arr19[0] &= $FF00
        arr19[1] &= $0F0F
        arr19[2] &= $00FF

        if arr19[0] == $FF00 and arr19[1] == $0000 and arr19[2] == $0000 {
            txt.print("PASS: &= const\n")
        } else {
            txt.print("FAIL: &= const (expected 65280,0,0 got ")
            txt.print_uw(arr19[0])
            txt.print(",")
            txt.print_uw(arr19[1])
            txt.print(",")
            txt.print_uw(arr19[2])
            txt.print(")\n")
        }

        ; Test split word array &= with variable
        uword[] arr20 = [$FFFF, $AAAA, $5555]
        uword maskval = $FF00
        arr20[0] &= maskval
        arr20[1] &= $00FF
        arr20[2] &= $FFFF

        if arr20[0] == $FF00 and arr20[1] == $00AA and arr20[2] == $5555 {
            txt.print("PASS: &= var\n")
        } else {
            txt.print("FAIL: &= var (expected 65280,170,21845 got ")
            txt.print_uw(arr20[0])
            txt.print(",")
            txt.print_uw(arr20[1])
            txt.print(",")
            txt.print_uw(arr20[2])
            txt.print(")\n")
        }

        ; ============================================================
        ; Bitwise OR tests
        ; ============================================================

        ; Test split word array |= with constant value
        uword[] arr21 = [$0000, $0F0F, $F0F0]
        arr21[0] |= $FFFF
        arr21[1] |= $F0F0
        arr21[2] |= $0F0F

        if arr21[0] == $FFFF and arr21[1] == $FFFF and arr21[2] == $FFFF {
            txt.print("PASS: |= const\n")
        } else {
            txt.print("FAIL: |= const (expected 65535,65535,65535 got ")
            txt.print_uw(arr21[0])
            txt.print(",")
            txt.print_uw(arr21[1])
            txt.print(",")
            txt.print_uw(arr21[2])
            txt.print(")\n")
        }

        ; Test split word array |= with variable
        uword[] arr22 = [$0001, $0010, $0100]
        uword orval = $FF00
        arr22[0] |= orval
        arr22[1] |= $00FF
        arr22[2] |= $F00F

        if arr22[0] == $FF01 and arr22[1] == $00FF and arr22[2] == $F10F {
            txt.print("PASS: |= var\n")
        } else {
            txt.print("FAIL: |= var (expected 65281,255,61711 got ")
            txt.print_uw(arr22[0])
            txt.print(",")
            txt.print_uw(arr22[1])
            txt.print(",")
            txt.print_uw(arr22[2])
            txt.print(")\n")
        }

        ; ============================================================
        ; Bitwise XOR tests
        ; ============================================================

        ; Test split word array ^= with constant value
        uword[] arr23 = [$FFFF, $0000, $AAAA]
        arr23[0] ^= $FF00
        arr23[1] ^= $FFFF
        arr23[2] ^= $5555

        if arr23[0] == $00FF and arr23[1] == $FFFF and arr23[2] == $FFFF {
            txt.print("PASS: ^= const\n")
        } else {
            txt.print("FAIL: ^= const (expected 255,65535,65535 got ")
            txt.print_uw(arr23[0])
            txt.print(",")
            txt.print_uw(arr23[1])
            txt.print(",")
            txt.print_uw(arr23[2])
            txt.print(")\n")
        }

        ; Test split word array ^= with variable
        uword[] arr24 = [$1234, $5678, $9ABC]
        uword xorval = $FFFF
        arr24[0] ^= xorval
        arr24[1] ^= $0000
        arr24[2] ^= $FFFF

        if arr24[0] == $EDCB and arr24[1] == $5678 and arr24[2] == $6543 {
            txt.print("PASS: ^= var\n")
        } else {
            txt.print("FAIL: ^= var (expected 60875,22136,25923 got ")
            txt.print_uw(arr24[0])
            txt.print(",")
            txt.print_uw(arr24[1])
            txt.print(",")
            txt.print_uw(arr24[2])
            txt.print(")\n")
        }

        ; ============================================================
        ; Non-split array *= with non-const tests
        ; ============================================================

        ; Test non-split array *= with constant index, non-const value
        uword[] @nosplit arr25 = [10, 20, 30]
        uword mulval25 = 7
        arr25[0] *= mulval25
        arr25[1] *= 3

        if arr25[0] == 70 and arr25[1] == 60 and arr25[2] == 30 {
            txt.print("PASS: nosplit *= const-idx, var\n")
        } else {
            txt.print("FAIL: nosplit *= const-idx, var (expected 70,60,30 got ")
            txt.print_uw(arr25[0])
            txt.print(",")
            txt.print_uw(arr25[1])
            txt.print(",")
            txt.print_uw(arr25[2])
            txt.print(")\n")
        }

        ; Test non-split array *= with non-constant index, constant value
        uword[] @nosplit arr26 = [10, 20, 30]
        ubyte idx26 = 2
        arr26[idx26] *= 5

        if arr26[0] == 10 and arr26[1] == 20 and arr26[2] == 150 {
            txt.print("PASS: nosplit *= var-idx, const\n")
        } else {
            txt.print("FAIL: nosplit *= var-idx, const (expected 10,20,150 got ")
            txt.print_uw(arr26[0])
            txt.print(",")
            txt.print_uw(arr26[1])
            txt.print(",")
            txt.print_uw(arr26[2])
            txt.print(")\n")
        }

        ; Test non-split array *= with non-constant index, non-constant value
        uword[] @nosplit arr27 = [5, 10, 15]
        ubyte idx27 = 1
        uword mulval27 = 8
        arr27[idx27] *= mulval27

        if arr27[0] == 5 and arr27[1] == 80 and arr27[2] == 15 {
            txt.print("PASS: nosplit *= var-idx, var\n")
        } else {
            txt.print("FAIL: nosplit *= var-idx, var (expected 5,80,15 got ")
            txt.print_uw(arr27[0])
            txt.print(",")
            txt.print_uw(arr27[1])
            txt.print(",")
            txt.print_uw(arr27[2])
            txt.print(")\n")
        }

        sys.poweroff_system()
    }
}
