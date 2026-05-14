/* lineclip.c - Line clipping algorithms
 *
 * CURRENT implementation: optimized for SMALLNESS using C's native float.
 *
 * FUTURE constraints for a planned integer-only 6502 target implementation:
 * (These are NOT enforced in the current code!)
 * -----------------------------------------------------
 * 1. NO floating point arithmetic.
 * 2. NO signed modulo operator (%).
 * 3. Results MUST be strictly inside the clip window.
 * 4. Return true if visible, false if fully clipped.
 * 5. Parametric t is scaled by 256.
 * 6. Keep intermediates in int16_t; use int32_t only when necessary.
 *    Input coords are int16_t (-32767..32767). Clip window is [0,0]-[639,479].
 * 7. NO recursion — iterative only.
 * 8. 16-bit signed division IS available. No 32-bit/64-bit division.
 *    Division by 256 is a right shift.
 */
#include "lineclip.h"
#include <stdbool.h>
#include <stddef.h>

/* (dist * num) / den — 32-bit intermediate, 16-bit result.
 * Used by both CS-int16 and Elite-style clipping.
 *
 * 6502 Implementation Notes:
 * ---------------------------
 * On a plain 6502 this expands to a 16x16→32 multiply followed by a
 * 32/16→16 division.  Neither operation is natively supported, so both
 * require software routines:
 *
 *   MUL16x16:  shift-add multiply, ~160 cycles
 *   DIV32x16:  restoring division, ~200 cycles
 *   TOTAL:     ~360 cycles per call
 *
 * Optimisation opportunities for 6502:
 * -------------------------------------
 * 1. When den is a power of two (e.g. 256), replace the division
 *    with a simple byte swap or right-shift.  This saves the entire
 *    DIV32x16 routine (~200 cycles).
 *
 * 2. When num is 256 (common in frac_mul(dx, t0, 256)), the 32-bit
 *    multiply reduces to a 16-bit left-shift by 8 (i.e. store dx in
 *    the high word).  The result is simply the high byte of the
 *    32-bit product — no actual multiply loop needed.
 *
 * 3. When |dist| < 256 (common for short clip distances), the 16x16
 *    multiply can be reduced to an 8x16 multiply, saving ~40 cycles.
 *
 * 4. Sign handling: track the sign separately, do unsigned math on
 *    absolute values, then negate if needed.  This avoids signed
 *    multiplication and division entirely, using simpler unsigned
 *    routines.
 *
 * 5. Zero-page placement: keep all 6 working variables in zero-page
 *    ($00-$09) to save 2 cycles per access.
 *
 * Combined, these optimizations can reduce a single frac_mul call
 * from ~360 cycles down to ~60-120 cycles depending on the inputs.
 */
static inline int16_t frac_mul(int16_t dist, int16_t num, int16_t den)
{
    return (int16_t)((int32_t)dist * num / den);
}

/* ========================================================================== */
/* Cohen-Sutherland: iterative region-code clipping (float)                   */
/* ========================================================================== */

static inline int _oc(int16_t x, int16_t y, int16_t x1, int16_t y1, int16_t x2, int16_t y2)
{
    int c = 0;
    if (x < x1) c |= 1;
    if (x > x2) c |= 2;
    if (y < y1) c |= 4;
    if (y > y2) c |= 8;
    return c;
}

/* After clipping against LEFT/RIGHT edge: x is exactly cx1 or cx2, so
 * LEFT=RIGHT=0. Only need to test the new y against cy1/cy2. */
static inline int _oc_after_x(int16_t y, int16_t cy1, int16_t cy2)
{
    int c = 0;
    if (y < cy1) c |= 4;
    if (y > cy2) c |= 8;
    return c;
}

/* After clipping against TOP/BOTTOM edge: y is exactly cy1 or cy2, so
 * TOP=BOTTOM=0. Only need to test the new x against cx1/cx2. */
static inline int _oc_after_y(int16_t x, int16_t cx1, int16_t cx2)
{
    int c = 0;
    if (x < cx1) c |= 1;
    if (x > cx2) c |= 2;
    return c;
}

bool clip_cohen_sutherland(int16_t *x1, int16_t *y1, int16_t *x2, int16_t *y2,
                           int16_t cx1, int16_t cy1, int16_t cx2, int16_t cy2)
{
    int16_t ax = *x1, ay = *y1, bx = *x2, by = *y2;
    int ca = _oc(ax, ay, cx1, cy1, cx2, cy2);
    int cb = _oc(bx, by, cx1, cy1, cx2, cy2);
    float dx = bx - ax, dy = by - ay;

    for (;;) {
        if ((ca | cb) == 0) { *x1 = ax; *y1 = ay; *x2 = bx; *y2 = by; return true; }
        if ((ca & cb) != 0) return false;

        int co = ca ? ca : cb;
        int16_t x = 0, y = 0;

        if      (co & 8) { x = ax + (int16_t)(dx * (cy2 - ay) / dy); y = cy2; }
        else if (co & 4) { x = ax + (int16_t)(dx * (cy1 - ay) / dy); y = cy1; }
        else if (co & 2) { y = ay + (int16_t)(dy * (cx2 - ax) / dx); x = cx2; }
        else             { y = ay + (int16_t)(dy * (cx1 - ax) / dx); x = cx1; }

        if (co == ca) {
            dx -= x - ax;
            dy -= y - ay;
            ax = x; ay = y;
            ca = (co & 0xC) ? _oc_after_y(ax, cx1, cx2) : _oc_after_x(ay, cy1, cy2);
        } else {
            dx -= bx - x;
            dy -= by - y;
            bx = x; by = y;
            cb = (co & 0xC) ? _oc_after_y(bx, cx1, cx2) : _oc_after_x(by, cy1, cy2);
        }
    }
}

/* ========================================================================== */
/* Cohen-Sutherland: iterative region-code clipping, pure int16_t            */
/* Uses frac_mul for the intersection math -- no float operations at all.    */
/* ========================================================================== */

bool clip_cohen_sutherland_int16(int16_t *x1, int16_t *y1, int16_t *x2, int16_t *y2,
                                 int16_t cx1, int16_t cy1, int16_t cx2, int16_t cy2)
{
    int16_t ax = *x1, ay = *y1, bx = *x2, by = *y2;
    int ca = _oc(ax, ay, cx1, cy1, cx2, cy2);
    int cb = _oc(bx, by, cx1, cy1, cx2, cy2);
    int16_t dx = bx - ax, dy = by - ay;

    for (;;) {
        if ((ca | cb) == 0) { *x1 = ax; *y1 = ay; *x2 = bx; *y2 = by; return true; }
        if ((ca & cb) != 0) return false;

        int co = ca ? ca : cb;
        int16_t x = 0, y = 0;

        if      (co & 8) { x = ax + frac_mul(cy2 - ay, dx, dy); y = cy2; }
        else if (co & 4) { x = ax + frac_mul(cy1 - ay, dx, dy); y = cy1; }
        else if (co & 2) { y = ay + frac_mul(cx2 - ax, dy, dx); x = cx2; }
        else             { y = ay + frac_mul(cx1 - ax, dy, dx); x = cx1; }

        if (co == ca) {
            dx -= x - ax;
            dy -= y - ay;
            ax = x; ay = y;
            ca = (co & 0xC) ? _oc_after_y(ax, cx1, cx2) : _oc_after_x(ay, cy1, cy2);
        } else {
            dx -= bx - x;
            dy -= by - y;
            bx = x; by = y;
            cb = (co & 0xC) ? _oc_after_y(bx, cx1, cx2) : _oc_after_x(by, cy1, cy2);
        }
    }
}

/* ========================================================================== */
/* Elite-inspired line clipping (BBC Micro, 6502)                           */
/* Based on the two-stage algorithm from Mark Moxon's Elite deep dive:       */
/* https://elite.bbcelite.com/deep_dives/line-clipping.html                  */
/*                                                                           */
/* Uses the line equation directly: x = ax + dx*t, y = ay + dy*t.           */
/* At an X edge: t = (edge - ax) / dx  →  Δy = dy * t                       */
/* At a Y edge: t = (edge - ay) / dy  →  Δx = dx * t                        */
/* ========================================================================== */

/* Test whether a point is inside the clip window */
static inline bool elite_inside(int16_t x, int16_t y, int16_t cx1, int16_t cy1,
                                int16_t cx2, int16_t cy2)
{
    return x >= cx1 && x <= cx2 && y >= cy1 && y <= cy2;
}

/* Clip one off-screen endpoint toward the other endpoint.
 * A moves toward B, B moves toward A (signs handled by caller).
 * Loops to handle secondary clipping after the first axis is fixed. */
static void elite_clip_one(int16_t *ax, int16_t *ay,
                            int16_t cx1, int16_t cy1,
                            int16_t cx2, int16_t cy2,
                            int16_t odx, int16_t ody)
{
    for (int iter = 0; iter < 2; iter++) {
        int16_t x = *ax, y = *ay;
        int did_clip = 0;

        if (x < cx1 && odx > 0) {
            y += frac_mul(cx1 - x, ody, odx);
            x = cx1;
            did_clip = 1;
        } else if (x > cx2 && odx < 0) {
            y += frac_mul(cx2 - x, ody, odx);
            x = cx2;
            did_clip = 1;
        }

        if (y < cy1 && ody > 0) {
            x += frac_mul(cy1 - y, odx, ody);
            y = cy1;
            did_clip = 1;
        } else if (y > cy2 && ody < 0) {
            x += frac_mul(cy2 - y, odx, ody);
            y = cy2;
            did_clip = 1;
        }

        *ax = x;
        *ay = y;
        if (!did_clip) break;
    }
}

/* Elite-style two-stage clipping.
 * Returns true if any part of the line is visible, false otherwise.
 * Source: https://elite.bbcelite.com/deep_dives/line-clipping.html */
bool clip_elite(int16_t *x1, int16_t *y1, int16_t *x2, int16_t *y2,
                int16_t cx1, int16_t cy1, int16_t cx2, int16_t cy2)
{
    int16_t ax = *x1, ay = *y1, bx = *x2, by = *y2;
    bool a_in = elite_inside(ax, ay, cx1, cy1, cx2, cy2);
    bool b_in = elite_inside(bx, by, cx1, cy1, cx2, cy2);

    /* Fast accept */
    if (a_in && b_in) return true;

    /* Quick reject: both off-screen on the same side */
    int a_code = 0, b_code = 0;
    if (ax < cx1) a_code |= 1;
    if (ax > cx2) a_code |= 2;
    if (ay < cy1) a_code |= 4;
    if (ay > cy2) a_code |= 8;
    if (bx < cx1) b_code |= 1;
    if (bx > cx2) b_code |= 2;
    if (by < cy1) b_code |= 4;
    if (by > cy2) b_code |= 8;
    if ((a_code & b_code) != 0) return false;

    int16_t ddx = bx - ax;
    int16_t ddy = by - ay;

    /* Clip off-screen endpoints */
    if (!a_in) elite_clip_one(&ax, &ay, cx1, cy1, cx2, cy2, ddx, ddy);
    if (!b_in) elite_clip_one(&bx, &by, cx1, cy1, cx2, cy2, -ddx, -ddy);

    /* Final validation */
    if (!elite_inside(ax, ay, cx1, cy1, cx2, cy2)) return false;
    if (!elite_inside(bx, by, cx1, cy1, cx2, cy2)) return false;

    *x1 = ax; *y1 = ay; *x2 = bx; *y2 = by;
    return true;
}
