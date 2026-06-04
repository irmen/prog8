%encoding iso
%import textio

Data {
    struct Record {
        ubyte id
        ubyte[4] data
        word[2]  scores
    }
}

main {
    sub start() {
        txt.iso()
        
        ; Initialize struct instance
        ^^Data.Record rec = ^^Data.Record : [1, 10, 20, 30, 40, 1000, 2000]
        
        txt.print("Record ID: ")
        txt.print_ub(rec.id)
        txt.print("\n")
        
        txt.print("Data[0]: ")
        txt.print_ub(rec.data[0])
        txt.print("\n")

        txt.print("Score[1]: ")
        txt.print_w(rec.scores[1])
        txt.print("\n")
        
        ; Modify array fields
        rec.data[2] = 99
        txt.print("Modified Data[2]: ")
        txt.print_ub(rec.data[2])
        txt.print("\n")
        
        txt.print("Sizeof Record: ")
        txt.print_ub(sizeof(Data.Record))
        txt.print("\n")
        
        txt.print("Offsetof id: ")
        txt.print_ub(offsetof(Data.Record.id))
        txt.print("\n")

        txt.print("Offsetof data: ")
        txt.print_ub(offsetof(Data.Record.data))
        txt.print("\n")

        txt.print("Offsetof scores: ")
        txt.print_ub(offsetof(Data.Record.scores))
        txt.print("\n")
        
        sys.poweroff_system()
    }
}
