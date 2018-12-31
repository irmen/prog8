%import c64utils

~ main {

    sub start()  {

        A+=5

        ; @todo if-else if should compile ?:
;        if A>100
;            Y=2
;        else if A>20
;            Y=3


;        @($d020) = 5
        @($d020)++
        @($d021)--
;        @($d020)=(@$d020)+5
        @($d020)+=5
;        @($d021)=(@$d021)-5
        @($d021)-=5

    }
}
