; Cooperative multitasking / Coroutines
; EXPERIMENTAL LIBRARY: Api may change or it may be removed completely in a future version!

; Achieves cooperative multitasking among a list of tasks each calling yield() to pass control to the next.
; Every task runs on its own private machine stack: switching tasks swaps the stack pointer (SP)
; and frame pointer (A5). Tasks are therefore regular subroutines: they may use stack frames,
; locals, parameters, nested calls, even (bounded) recursion. Only state in shared static
; storage (globals, block variables) is visible to all tasks, just like with threads.
;
; Features:
; - can have a dynamic number of active tasks (max 64), when a task ends it is automatically removed from the task list.
; - you can add new tasks, while the rest is already running.  Just not yet from inside IRQ handlers!
; - tasks are regular subroutines but have to call yield() to pass control to the next task (round-robin).
;   yield() may be called from any depth: helper subroutines called by a task may yield on its behalf.
; - yield() returns the registered userdata value for the resumed task, so a single subroutine can be
;   used as multiple tasks on different userdata. Per-task state lives in locals, parameters and
;   frames (all private to the task); use the userdata value only for what must additionally be shared.
; - you can kill a task (if you know its id...)
; - when all tasks are finished the run() call will also return.
; - each task gets a fixed STACKSIZE byte stack (16 Kb total for 64 tasks). Deep call chains and
;   large locals must fit; there is no overflow detection.
; - this library is not (yet) usable from IRQ handlers. Don't do it. It will end badly.  (can't manipulate the task list simultaneously)
;
; Difference from IRQ handlers:
; - you can have many tasks instead of only 2 (main program + irq handler)
; - it's not tied to any IRQ setup, and will run as fast as the tasks themselves allow
; - tasks fully control the switch to the next task; there is no preemptive switching
;
; USAGE:
; - call add(taskaddress, userdata) to add a new task.  It returns the task id.
;   A task is a parameterless subroutine (its stack is entered directly, no arguments are passed).
; - call run(supervisor) to start executing all tasks until none are left. Pass 0 or a pointer to a 'supervisor' routine.
;   that routine can for instance call current() (or just look at the active_task variable) to get the id of the next task to execute.
;   It has then to return a boolean: true=next task is to be executed, false=skip the task this time.
; - in tasks: call yield() to pass control to the next task. Use the returned userdata value to do different things.
; - call current() to get the current task id.
; - call kill(taskid) to kill a task by id.
; - call killall() to kill all tasks.
; - tasks that keep state in shared static storage (globals, block variables) are flagged by the
;   compiler's re-entrancy check; mark such a task %option noframe if the sharing is intentional.
;
; TIP: HOW TO WAIT without BLOCKING other coroutines?
; Make sure you call yield() in the waiting loop, for example:
;            long timer = cbm.RDTIM16() + 60
;            while cbm.RDTIM16() != timer
;                void coroutines.yield()

coroutines {
    %option ignore_unused, merge

    const ubyte MAX_TASKS = 64
    ; NOTE: STACKSIZE must match the shift and top offset in the stack setup code in add() below.
    const uword STACKSIZE = 256
    pointer[MAX_TASKS] tasklist
    pointer[MAX_TASKS] userdatas
    pointer[MAX_TASKS] task_sp
    pointer[MAX_TASKS] task_a5
    ubyte[MAX_TASKS*STACKSIZE] taskstacks
    ubyte active_task
    pointer supervisor
    pointer main_sp
    pointer main_a5
    pointer tmp_sp
    pointer tmp_a5
    pointer tmp_term

    sub add(pointer taskaddress, pointer userdata) -> ubyte {
        %option noframe
        ; find the next empty slot in the tasklist and stick it there
        ; returns the task id of the new task, or 255 if there was no space for more tasks. 0 is a valid task id!
        ; also returns the success in the Carry flag (carry set=success, carry clear = task was not added)
        ubyte taskid
        for taskid in 0 to len(tasklist)-1 {
            if tasklist[taskid] == 0 {
                tasklist[taskid] = taskaddress
                userdatas[taskid] = userdata
                ; prepare the task's private stack so it looks like the task was just called:
                ; stack top holds [termination address][task entry address], SP pointing at the entry.
                ; A5 can be anything valid: the task's first 'link a5' only pushes it, never reads it.
                tmp_term = &termination
                %asm {{
                    moveq   #0,d0
                    move.b  p8b_coroutines.p8s_add.p8v_taskid,d0
                    lsl.l   #8,d0
                    lea     p8b_coroutines.p8v_taskstacks,a0
                    adda.l  d0,a0
                    adda.l  #248,a0
                    move.l  p8b_coroutines.p8s_add.p8v_taskaddress,(a0)
                    move.l  p8b_coroutines.p8v_tmp_term,4(a0)
                    move.l  a0,p8b_coroutines.p8v_tmp_sp
                }}
                task_sp[taskid] = tmp_sp
                task_a5[taskid] = tmp_sp
                sys.set_carry()
                return taskid
            }
        }
        ; no space for new task
        sys.clear_carry()
        return 255
    }

    sub killall() {
        ; kill all existing tasks
        ubyte taskid
        for taskid in 0 to len(tasklist)-1 {
            kill(taskid)
        }
    }

    sub run(pointer supervisor_routine) {
        %option noframe
        supervisor = supervisor_routine
        for active_task in 0 to len(tasklist)-1 {
            if tasklist[active_task]!=0 {
                ; enter the first task on its own stack. Control comes back here never:
                ; when the last task ends, the main context is restored and execution
                ; resumes in whoever called run().
                %asm {{
                    move.l  sp,p8b_coroutines.p8v_main_sp
                    move.l  a5,p8b_coroutines.p8v_main_a5
                }}
                tmp_sp = task_sp[active_task]
                tmp_a5 = task_a5[active_task]
                %asm {{
                    move.l  p8b_coroutines.p8v_tmp_sp,sp
                    movea.l p8b_coroutines.p8v_tmp_a5,a5
                }}
                return
            }
        }
    }

    sub yield() -> pointer {
        %option noframe
        ; Suspend the current task and resume the next live one (round-robin).
        ; Returns the userdata value registered for the resumed task.
        %asm {{
            move.l  sp,p8b_coroutines.p8v_tmp_sp
            move.l  a5,p8b_coroutines.p8v_tmp_a5
        }}
        task_sp[active_task] = tmp_sp
        task_a5[active_task] = tmp_a5
        if not pick_next() {
            ; no tasks left: restore the main context, back to whoever called run()
            %asm {{
                move.l  p8b_coroutines.p8v_main_sp,sp
                movea.l p8b_coroutines.p8v_main_a5,a5
            }}
            return 0
        }
        tmp_sp = task_sp[active_task]
        tmp_a5 = task_a5[active_task]
        %asm {{
            move.l  p8b_coroutines.p8v_tmp_sp,sp
            movea.l p8b_coroutines.p8v_tmp_a5,a5
        }}
        return userdatas[active_task]
    }

    sub pick_next() -> bool {
        %option noframe
        ; advance active_task to the next live task (round-robin), honouring supervisor vetoes
        repeat len(tasklist) {
            active_task++
            if active_task==len(tasklist)
                active_task=0
            if tasklist[active_task]!=0 {
                if supervisor==0
                    return true
                if lsb(call(supervisor))!=0
                    return true
            }
        }
        return false    ; no task
    }

    sub kill(ubyte taskid) {
        tasklist[taskid] = 0
    }

    sub current() -> ubyte {
        return active_task
    }

    sub termination() -> pointer {
        %option noframe
        ; internal routine, entered when a task returns: wipe it from the list, resume the next task.
        ; Returns the next task's userdata (to the resumed task, via its stack).
        kill(active_task)
        if not pick_next() {
            ; no tasks left: restore the main context, back to whoever called run()
            %asm {{
                move.l  p8b_coroutines.p8v_main_sp,sp
                movea.l p8b_coroutines.p8v_main_a5,a5
            }}
            return 0
        }
        tmp_sp = task_sp[active_task]
        tmp_a5 = task_a5[active_task]
        %asm {{
            move.l  p8b_coroutines.p8v_tmp_sp,sp
            movea.l p8b_coroutines.p8v_tmp_a5,a5
        }}
        return userdatas[active_task]
    }
}
