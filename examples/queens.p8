%import textio

; Recursive N-Queens solver.
; The problem is: find all possible ways to place 8 Queen chess pieces on a chess board, so that none of them attacks any other.
; (this program prints all solutions without taking mirroring and flipping the chess board into account)
; Note: this program can be compiled for multiple target systems.

main {
    const ubyte NUMQUEENS=8
    ubyte[NUMQUEENS] board

    sub could_place(ubyte row, ubyte col) -> bool {
        if row==0
            return true
        ubyte i
        for i in 0 to row-1 {
            if board[i]==col or board[i]-i==col-row or board[i]+i==col+row
                return false
        }
        return true
    }

    ubyte solution_count
    sub print_solution() {
        solution_count++
        txt.home()
        txt.print("found solution ")
        txt.print_ub(solution_count)
        txt.nl()
        ubyte i
        for i in 0 to NUMQUEENS-1 {
            ubyte col = board[i]
            txt.chrout(' ')
            repeat col txt.chrout('.')
            txt.chrout('q')
            repeat NUMQUEENS-col-1 txt.chrout('.')
            txt.nl()
        }
    }

    sub place_queen(ubyte row) {
        if row == NUMQUEENS {
            print_solution()
            return
        }
        ubyte col
        for col in 0 to NUMQUEENS-1 {
            if could_place(row, col) {
                board[row] = col
                ; we need to save the local variables row and col.
                push(row)
                push(col)
                place_queen(row + 1)
                ; restore the local variables after the recursive call.
                col = pop()
                row = pop()
                board[row] = 0
            }
        }
    }

    sub start() {
        cbm.SETTIM(0,0,0)
        txt.clear_screen()
        place_queen(0)
        txt.nl()
        uword duration=100*cbm.RDTIM16()/6
        txt.print_uw(duration)
        txt.print(" milliseconds\n")
        repeat {
        }
    }
}
