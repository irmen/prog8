; Draw a fractal tree recursively
; example of doing recursion with local per-call state that has to be
; preserved across recursive calls, as opposed to queens.p8 where the recursive
; function only modifies global state.
%import graphics
%import floats
%zeropage basicsafe

main {
     ; Adjust these parameters to change the appearance of the tree:
     ; 1. the angle of each branch away from its parent (left and right)
     const float delta_theta   = floats.π / 6 

     ; 2. the size of each branch relative to its parent
     const float shrink_factor = 2.0 / 3.0

     ; These stacks hold copies of the parameters during recursive calls
     uword[51] x
     ubyte[51] y
     float[51] theta
     float[51] length

     ; This points to the current level of stack in use
     ubyte stack_pointer

     ; draw a branch starting at (x1, y1) at angle th1 with length len1
     ; recurses if length is large enough
     sub draw_branch(uword x1, ubyte y1, float th1, float len1) {

         uword x2 = (x1 as float + len1 * floats.cos(th1)) as uword
         ubyte y2 = (y1 as float - len1 * floats.sin(th1)) as ubyte
         graphics.line(x1, y1, x2, y2)

         if len1 > 2 {
             ; push parameters onto stacks
             x[stack_pointer] = x1
             y[stack_pointer] = y1
             theta[stack_pointer] = th1
             length[stack_pointer] = len1
             stack_pointer += 1

             draw_branch(x2, y2, th1 - delta_theta, len1 * shrink_factor)

             ; recover parameters after recursive call
             x1   = x[stack_pointer - 1]
             y1   = y[stack_pointer - 1]
             th1  = theta[stack_pointer - 1]
             len1 = length[stack_pointer - 1]

             x2 = (x1 as float + len1 * floats.cos(th1)) as uword
             y2 = (y1 as float - len1 * floats.sin(th1)) as ubyte

             draw_branch(x2, y2, th1 + delta_theta, len1 * shrink_factor)

             ; pop stack level
             stack_pointer -= 1
         }
     }

     sub start() {
         graphics.enable_bitmap_mode()
         draw_branch(graphics.WIDTH / 2, graphics.HEIGHT - 1,
                     floats.π / 2,
                     graphics.HEIGHT / 2 * shrink_factor)

         ; done rendering; wait for a key before exiting
         do {
             ubyte key = cbm.GETIN2()
         } until key != 0
         graphics.disable_bitmap_mode()
     }
}
