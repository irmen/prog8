%import serial
%import textio
%zeropage basicsafe
%encoding iso

; use cx16 wifi/serial card to download something from the internet

main {
    sub start() {
        txt.iso()
        uword uart1, uart2 = serial.detect_uarts()

        if uart1 != 0 {
            txt.print("uart chips at: ")
            txt.print_uwhex(uart1, true)
            txt.spc()
            txt.print_uwhex(uart2, true)
            txt.nl()

            serial.zi_initialize(uart1)     ; init ZiModem on uart1   (this is how it is configured on the Wifi/serial expansion card)
            txt.print("\nYour ip address is: ")
            txt.print(serial.zi_get_ip_address())
            txt.nl()
            txt.nl()

            long size = serial.zi_start_get_file("http://mockhttp.org/get?arg=hello-from-prog8-serial")
            if size>0 {
                txt.print("downloading file of size: ")
                txt.print_l(size)
                txt.nl()

                ubyte[256] buffer
                while size>0 {
                    uword readsize = serial.zi_get_file_chunk(&buffer, sizeof(buffer), size)
                    size -= readsize
                    for cx16.r0L in 0 to lsb(readsize-1) {
                        txt.chrout(buffer[cx16.r0L])
                    }
                }

                txt.nl()

            } else {
                txt.print("file not found\n")
            }
            serial.zi_reset()

        } else {
            txt.print("no uart chips found\n")
        }
    }
}
