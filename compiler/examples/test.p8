%import c64utils
%option enable_floats

~ main {

    sub start()  {

        word[3]   wa = [-1000.w,2000.w,3000.w]      ; @todo array data type fix (float->word)
        word[3]   wa2 = [1000,2000,3000]      ; @todo array data type fix (uword->word)

        byte[3] ba1 = [-1, 2, 3]
        byte[3] ba2 = [100,101,102]     ; @todo array data type

        ;return b2ub(fintb(x * flt(width)/4.2) + width//2)

    }
}

