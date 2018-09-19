%output prg
%launcher basic
%option enable_floats

~ main {

sub start() -> () {

    if(X) goto yesx
    else goto nox


yesx:

    if(X) {
        A=0
        goto yesx       ;; @todo fix undefined symbol error
        return
    } else {
        A=1
        goto nox
    }

    return

        sub bla() -> () {
            return
            sub fooz() -> () {
                return
            }

            sub fooz2() -> () {
                return
            }
            return

        }

        A=45

nox:
    word i

    for i in 10 to 20 {
        if(i>12) goto fout   ;; @todo fix undefined symbol error
        break
        continue


        bla()   ;; @todo fix undefined symbol error
    }

fout:
    return
}
}
