%import textio

; Recursive N-Queens solver.
; The problem is: find all possible ways to place 8 Queen chess pieces on a chess board, so that none of them attacks any other.
; (this program prints all solutions without taking mirroring and flipping the chess board into account)
; Note: this program can be compiled for multiple target systems.

queens {
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

    uword solution_count
    uword maximum_duration

    sub place_queen(ubyte row) -> bool {
        if row == NUMQUEENS {
            solution_count++
            txt.chrout('.')
            return cbm.RDTIM16()<maximum_duration
        }
        bool continue_running=true
        ubyte col
        for col in 0 to NUMQUEENS-1 {
            if could_place(row, col) {
                board[row] = col
                ; we need to save the local variables row and col.
                push(row)
                push(col)
                continue_running = place_queen(row + 1)
                ; restore the local variables after the recursive call.
                col = pop()
                row = pop()
                board[row] = 0

                if not continue_running
                    break
            }
        }
        return continue_running
    }

    sub bench(uword max_time) -> uword {
        solution_count = 0
        maximum_duration = max_time
        txt.nl()
        cbm.SETTIM(0,0,0)
        while cbm.RDTIM16() < maximum_duration {
            void place_queen(0)
        }
        return solution_count
    }
}
