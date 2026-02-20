; Cooperative multitasking / Coroutines
; EXPERIMENTAL LIBRARY: Api may change or it may be removed completely in a future version!

; Achieves cooperative multitasking among a list of tasks each calling yield() to pass control to the next.
; Uses cpu stack return address juggling to cycle between different tasks.
;
; Features:
; - can have a dynamic number of active tasks (max 64), when a task ends it is automatically removed from the task list.
; - you can add new tasks, while the rest is already running.  Just not yet from inside IRQ handlers!
; - tasks are regular subroutines but have to call yield() to pass control to the next task (round-robin)
; - yield() returns the registered userdata value for that task, so a single subroutine could be used as multiple tasks on different userdata
;   BUT!! in that case, the subroutine cannot have any variables of its own that keep state, because they're shared across the multiple tasks!
;   (if a subroutine is just inserted as a task exactly ONCE, it's okay to use normal variables for state, because nobody will share them)
; - you can kill a task (if you know it's id...)
; - when all tasks are finished the run() call will also return.
; - tasks can't push anything on the cpu stack before calling yield() - that will cause chaos.
; - this library is not (yet) usable from IRQ handlers. Don't do it. It will end badly.  (can't manipulate the task list simultaneously)
;
; Difference from IRQ handlers:
; - you can have many tasks instead of only 2 (main program + irq handler)
; - it's not tied to any IRQ setup, and will run as fast as the tasks themselves allow
; - tasks fully control the switch to the next task; there is no preemptive switching
; - tasks will need to save/restore their own state, maybe by useing the userdata (pointer?) and/or task id for that.
;
; USAGE:
; - call add(taskaddress) to add a new task.  It returns the task id.
; - call run(supervisor) to start executing all tasks until none are left. Pass 0 or a pointer to a 'supervisor' routine.
;   that routine can for instance call current() (or just look at the active_task variable) to get the id of the next task to execute.
;   It has then to return a boolean: true=next task is to be executed, false=skip the task this time.
; - in tasks: call yield() to pass control to the next task. Use the returned userdata value to do different things.
;   For now, you MUST call yield() only from the actual subroutine that has been registered as a task!
;   (this is because otherwise the cpu call stack gets messed up and an RTS in task1 could suddenly pop a return address belonging to another tasks' call frame)
; - call current() to get the current task id.
; - call kill(taskid) to kill a task by id.
; - call killall() to kill all tasks.
; - IMPORTANT:  if you add the same subroutine multiple times, IT CANNOT DEPEND ON ANY LOCAL VARIABLES OR R0-R15 TO KEEP STATE. NOT EVEN REPEAT LOOP COUNTERS.
;   Those are all shared in the different tasks! You HAVE to use a mechanism around the userdata value (pointer?) to keep separate state elsewhere!
; - IMPORTANT:  ``defer`` cannot be used inside a coroutine that is reused for multiple tasks!!!
;
; TIP: HOW TO WAIT without BLOCKING other coroutines?
; Make sure you call yield() in the waiting loop, for example:
;            uword timer = cbm.RDTIM16() + 60
;            while cbm.RDTIM16() != timer
;                void coroutines.yield()

coroutines {
    %option ignore_unused

    const ubyte MAX_TASKS = 64
    uword[MAX_TASKS] tasklist
    uword[MAX_TASKS] userdatas
    ubyte active_task
    uword supervisor

    sub add(uword @nozp taskaddress, uword @nozp userdata) -> ubyte {
        ; find the next empty slot in the tasklist and stick it there
        ; returns the task id of the new task, or 255 if there was no space for more tasks. 0 is a valid task id!
        ; also returns the success in the Carry flag (carry set=success, carry clear = task was not added)
        for cx16.r0L in 0 to len(tasklist)-1 {
            if tasklist[cx16.r0L] == 0 {
                tasklist[cx16.r0L] = sys.get_as_returnaddress(taskaddress)
                userdatas[cx16.r0L] = userdata
                sys.set_carry()
                return cx16.r0L
            }
        }
        ; no space for new task
        sys.clear_carry()
        return 255
    }

    sub killall() {
        ; kill all existing tasks
        for cx16.r0L in 0 to len(tasklist)-1 {
            kill(cx16.r0L)
        }
    }

    sub run(uword @nozp supervisor_routine) {
        supervisor = supervisor_routine
        for active_task in 0 to len(tasklist)-1 {
            if tasklist[active_task]!=0 {
                ; activate the termination handler and start the first task
                ; note: cannot use pushw() because JSR doesn't push the return address in the same way
                sys.push_returnaddress(&termination)
                pushw(tasklist[active_task])
                return
            }
        }
    }

    sub yield() -> uword {
        ; Store the return address of the yielding task, and continue with the next one instead (round-robin)
        ; Returns the associated userdata value.
        ; NOTE: CAN ONLY BE CALLED FROM THE SCOPE OF THE SUBROUTINE THAT HAS BEEN REGISTERED AS THE TASK!
        uword task_return_address
        tasklist[active_task] = popw()

skip_task:
        if not next_task() {
            void popw()     ; remove return to the termination handler
            return 0   ; exiting here will now actually return back to the calling program that called run()
        }

        if supervisor!=0
            if lsb(call(supervisor))==0
                goto skip_task

        ; returning from yield then continues with the next coroutine:
        pushw(task_return_address)
        return userdatas[active_task]

        sub next_task() -> bool {
            ; search through the task list for the next active task
            repeat len(tasklist) {
                active_task++
                if active_task==len(tasklist)
                    active_task=0
                task_return_address = tasklist[active_task]
                if task_return_address!=0 {
                    return true
                }
            }
            return false    ; no task
        }
    }

    sub kill(ubyte @nozp taskid) {
        tasklist[taskid] = 0
    }

    sub current() -> ubyte {
        return active_task
    }

    sub termination() {
        ; internal routine: a task has terminated. wipe it from the list.
        kill(active_task)
        ; reactivate this termination handler and go to the next task
        sys.push_returnaddress(&termination)
        goto coroutines.yield.skip_task
    }
}
