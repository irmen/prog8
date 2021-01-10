%import test_stack
%import textio
%import diskio
%import string
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        str s1 = "12345 abcdef..uvwxyz ()!@#$%;:&*()-=[]<>\xff\xfa\xeb\xc0\n"
        str s2 = "12345 ABCDEF..UVWXYZ ()!@#$%;:&*()-=[]<>\xff\xfa\xeb\xc0\n"
        str s3 = "12345 \x61\x62\x63\x64\x65\x66..\x75\x76\x77\x78\x79\x7a ()!@#$%;:&*()-=[]<>\xff\xfa\xeb\xc0\n"

        txt.lowercase()

        txt.print(s1)
        txt.print(s2)
        txt.print(s3)

        string.lower(s1)
        string.lower(s2)
        string.lower(s3)
        txt.print(s1)
        txt.print(s2)
        txt.print(s3)

        string.upper(s1)
        string.upper(s2)
        string.upper(s3)

        txt.print(s1)
        txt.print(s2)
        txt.print(s3)

        txt.nl()
    }


}
