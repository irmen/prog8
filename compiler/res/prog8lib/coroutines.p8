; Cooperative multitasking / Coroutines
; EXPERIMENTAL LIBRARY: Api may change or it may be removed completely in a future version!

; Achieves cooperative multitasking among a list of tasks each calling yield() to pass control to the next.
; Uses cpu stack return address juggling to cycle between different tasks.
;
; Features:
; - can have a dynamic number of tasks (max 64), when tasks end they're automaticall removed from the task list.
; - you can add new tasks, even from IRQ handlers, while the rest is already running.
; - tasks are regular subroutines but have to call yield() to pass control to the next task (round-robin)
; - you can kill a task (if you know it's id...)
; - when all tasks are finished the run() call will also return.
; - tasks can't push anything on the cpu stack before calling yield() - that will cause chaos.
;
; Difference from IRQ handlers:
; - you can have many tasks instead of only 2 (main program + irq handler)
; - it's not tied to any IRQ setup, and will run as fast as the tasks themselves allow
; - tasks fully control the switch to the next task; there is no preemptive switching
;
; TODO to make it actually even more useful, we probably have to:
; - return a unique value (pointer that you had to provide when adding the task to the list?)
;   from yield() that the subroutine could use to access unique state,
;   because right now a single task == a single subroutine; right now you cannot re-use a subroutine to run
;   the same task multiple times for different things.
;
; USAGE:
; - call add(taskaddress) to add a new task.  It returns the task id.
; - call run() to start executing all tasks until none are left.
; - in tasks: call yield() to pass control to the next task.
; - call kill(tasknumber) to kill a task by id.
; - call killall() to kill all tasks.

coroutines {
    const ubyte MAX_TASKS = 64
    uword[MAX_TASKS] tasklist
    uword[MAX_TASKS] returnaddresses
    ubyte active_task

    sub add(uword taskaddress) -> ubyte {
        ; find the next empty slot in the tasklist and stick it there
        ; returns the task id of the new task, or 255 if there was no space for more tasks. 0 is a valid task id!
        ; also returns the success in the Carry flag (carry set=success, carry clear = task was not added)
        sys.irqsafe_set_irqd()
        for cx16.r0L in 0 to len(tasklist)-1 {
            if tasklist[cx16.r0L] == 0 {
                tasklist[cx16.r0L] = taskaddress
                returnaddresses[cx16.r0L] = 0
                sys.irqsafe_clear_irqd()
                sys.set_carry()
                return cx16.r0L
            }
        }
        sys.irqsafe_clear_irqd()
        ; no space for new task
        sys.clear_carry()
        return 255
    }

    sub killall() {
        ; kill all existing tasks
        sys.irqsafe_set_irqd()
        for cx16.r0L in 0 to len(tasklist)-1 {
            kill(cx16.r0L)
        }
        sys.irqsafe_clear_irqd()
    }

    sub run() {
        for active_task in 0 to len(tasklist)-1 {
            if tasklist[active_task]!=0 {
                ; activate the termination handler and start the first task
                ; note: cannot use pushw() because JSR doesn't push the return address in the same way
                sys.push_returnaddress(&termination)
                goto tasklist[active_task]
            }
        }
    }

    sub yield() {
        ; store the return address of the yielding task,
        ; and continue with the next one instead (round-robin)
        uword task_start, task_continue
        returnaddresses[active_task] = sys.popw()

resume_with_next_task:
        if not next_task() {
            void sys.popw()     ; remove return to the termination handler
            return   ; exiting here will now actually return from the start() call back to the calling program :)
        }

        if task_continue==0 {
            ; fetch start address of next task.
            ; address on the stack must be pushed in reverse byte order
            ; also, subtract 1 from the start address because JSR pushes returnaddress minus 1
            ; note: cannot use pushw() because JSR doesn't push the return address in the same way
            sys.push_returnaddress(task_start)
        } else
            sys.pushw(task_continue)
        ; returning from yield then continues with the next coroutine

        sub next_task() -> bool {
            ; search through the task list for the next active task
            sys.irqsafe_set_irqd()
            repeat len(tasklist) {
                active_task++
                if active_task==len(returnaddresses)
                    active_task=0
                task_start = tasklist[active_task]
                if task_start!=0 {
                    task_continue = returnaddresses[active_task]
                    sys.irqsafe_clear_irqd()
                    return true
                }
            }
            sys.irqsafe_clear_irqd()
            return false    ; no task
        }
    }

    sub kill(ubyte tasknum) {
        tasklist[tasknum] = 0
        returnaddresses[tasknum] = 0
    }

    sub termination() {
        ; a task has terminated. wipe it from the list.
        ; this is an internal routine
        kill(active_task)
        ; reactivate this termination handler
        ; note: cannot use pushw() because JSR doesn't push the return address in the same way
        sys.push_returnaddress(&termination)
        goto coroutines.yield.resume_with_next_task
    }
}
