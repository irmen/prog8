%output prg
%launcher basic

%import c64lib

~ not_main $d000 {
    byte [100] array1 = 0
    word [100] array2 = 1
    word [4] array2b = [1,2,3,4]
    byte [2,3] matrix1 = 2
    byte [2,3] matrix2 = [1,2,3,4,5,6]

    const byte [100] carray1 = 2
    const word [100] carray2 = 1.w
    const byte [2,3] cmatrix1 = [1,2,3,4,5,255]


    const float len1 =  len([1,2,3])
    const float round1 =  len([1,2,3])
    const float sin1 =  len([1,2,3])
    float cos1 =  len([1,2,3])

}

~ main $c003  {
    const byte [2,3] cmatrix1 = [1,2,3,4,5,255]
  word lsb1 = lsb($ea31)
  word msb1 = msb($ea31)
  byte lsb2 = lsb($ea31)
  byte msb2 = msb($ea31)
  word lsb3 = lsb($ea)
  word msb3 = msb($ea)
  const byte matrixsize = len(cmatrix1)
  const byte matrixsize2 = len(not_main.cmatrix1)

  const word len1 = len([1,2,3,wa1, wa2, ws1, all1])
  const word wa1 = ceil(abs(-999.22))
  const byte wa1b = abs(99)
  const byte wa1c = -(1-99)
  const byte wa1d = -0
  const byte wa2 = abs(-99)
  const byte wa2b= abs(-99.w)
  const byte wa2c = abs(-99)
  const word wa2d = abs(-999)
  float wa3 = abs(-1.23456)
  const float wa4 = abs(-133)
  const float avg1 = avg([-1.23456, 99999])
  const float sum1 = sum([-1.23456, 99999])
  const word ws1 = floor(sum([1,2,3,4.9]))
  const word ws2 = ceil(avg([1,2,3,4.9]))
  const word ws3 = round(sum([1,2,3,4.9]))
  const word any1 = any([0,0,0,0,0,22,0,0])
  const word any2 = any([2+sin(2.0), 2])
  const word all1 = all([0,0,0,0,0,22,0,0])
  const word all2 = all([0.0])
  const word all3 = all([wa1, wa2, ws1, all1])

  const word max1 = max([-1,-2,3.33,99+22])

  const word min1 = min([1,2,3,99+22])
    word dinges = round(not_main.len1)

    wa3 = rnd()
    wa3 = rndw()
    wa3 = rndf(22)

    A += 8
    A += rnd()
    A =A+ rnd()


	A = X>2
	X = Y>Y

    byte myByteChar = "A"
    word myWordChar = "B"
  word[1000]  ascending = 10 to 1009
  str  ascending3 = "a" to "z"
  str  ascending5 = "z" to "z"
  const byte cc = 4 + (2==9)
  byte cc2 = 4 - (2==10)
;  memory byte derp = max([$ffdd])          ; @todo implement memory vars in stackvm
;  memory byte derpA = abs(-20000)
;  memory byte derpB = max([1, 2.2, 4.4, 100])
;  memory byte cderp = min([$ffdd])+ (1/1)
;  memory byte cderpA = min([$ffdd, 10, 20, 30])
;  memory byte cderpB = min([1, 2.2, 4.4, 100])
;  memory byte derp2 = 2+$ffdd+round(10*sin(3.1))
  const byte hopla=55-33
  const byte hopla3=100+(-hopla)
  const byte hopla4 = 100-hopla
  const byte hopla1=main.hopla
  const float blerp1 = zwop / 2.22
  const float zwop = -1.7014118345e+38
  const float zwop2 = -1.7014118345e+38
  const float blerp2 = zwop / 2.22
  const byte equal = 4==4
  const byte equal2 = (4+hopla)>0

    ; goto 64738

    A++
;    derp++
    cc2--

  goto mega
  if_eq goto mega

  if_eq {
    A=99
  } else {
    A=100
  }


  byte equalQQ = 4==4
  const byte equalQQ2 = (4+hopla)>0
  const str string1 = "hallo"
  str string2 = "doei"

  equalQQ++
  AX++
  A=msb(X + round(sin(Y)+ 1111))
  A=lsb(X + round(sin(Y)+1111))
  equalQQ= X
  equalQQ= len([X, Y, AX])
  equalQQ= len("abcdef")
  equalQQ= len([1,2,3])
  equalQQ= len(string1)
  equalQQ= len(string2)
  P_carry(1)
  P_irqd(0)

  ;equalQQ = foo(33)
  ;equalQQ = main.foo(33)
  foo(33)
  main.foo(33)
  XY = hopla*2+hopla1

  byte equalWW = 4==4
  const byte equalWW2 = (4+hopla)>0

	if (1==1) goto cool

	if (1==2) return 44

	if (2==2) A=4

	if (3==3) X=5 else A=99

	if (5==5) {
		A=99
	}

	if(6==6) {
		A=lsb(sin(X))
		X=max([1,233,Y])
		X=min([1,2,Y])
		X=lsl(12)
		X=lsl(Y)
		P_carry(0)
		P_carry(1)
		P_carry(1-1)
		P_irqd(0)
		P_irqd(1)
	}  else X=33

        X= extra233.thingy()


	if(6>36) {
		A=99
	}  else {
		X=33
	}

  main.foo(1,2,3)
  return

  sub start () -> () {
    word dinges = 0
    word blerp1 =999
    word blerp3 = 1
    byte blerp2 =99
    float flob =1.1
    dinges=blerp1
    blerp3 = blerp2
    A=blerp2
    A=$02
    ;A=$02.w     ; @todo error word/byte
    ;A=$002      ; @todo error word/byte
    ;A=$00ff     ; @todo error word/byte
    A=%11001100
    ; A=%0010011001   ;@todo error word/byte
    ; A=99.w  ; @todo error word/byte
    XY=blerp1
    X=blerp2
    return
  }


mega:
	X += 1
cool:
	Y=2
	goto start

  sub foo () -> () {
  	byte blerp = 3
  	A=99
  	; return 33
  	return
  	ultrafoo()
  	X =33

mega:
cool:
  	return

    sub ultrafoo() -> () {
        X= extra233.thingy()
        ;return 33
        return
        goto main.mega
    }

  }

some_label_def:   A=44
  ;return 1+999
  return
}

%import imported
%import imported
%import imported2
%import imported2
