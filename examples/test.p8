%zeropage basicsafe
%option no_sysinit, romable

main {

    sub start()  {
        str name = "irmen"
        ubyte[] array1 = [11,22,33,44]
        ubyte[10] array2
        &ubyte[10] memorymappedarray = 1000
        &ubyte[10] memorymappedarray_zp = 200

        name[1] = 'a'
        array1[1] = 'b'
        array2[1] = 'c'
        memorymappedarray[2] = 99
        memorymappedarray_zp[2] = 99
    }
}
