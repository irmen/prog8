/* test_clip.c - Console test program for line clipping algorithms */
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include "lineclip.h"

/* Clip window: (400,300) to (1600,1400) */
#define CLIP_X1 400
#define CLIP_Y1 300
#define CLIP_X2 1600
#define CLIP_Y2 1400

typedef struct {
    int16_t x1, y1, x2, y2;
    int16_t ex1, ey1, ex2, ey2;
    const char *name;
} TestCase;

static const TestCase tests[] = {
    /* Inside the clip window */
    {  800,  600, 1200, 1000,  800,  600, 1200, 1000, "T1 inside" },
    {  420,  320,  500,  400,  420,  320,  500,  400, "T2 near TL" },
    { 1580, 1380, 1600, 1400, 1580, 1380, 1600, 1400, "T3 near BR" },
    { 1000,  850, 1000,  850, 1000,  850, 1000,  850, "T4 point inside" },

    /* Completely outside -- far away */
    {  -500, -200, -300,  100,    0,    0,    0,    0, "T5 far left" },
    {  2200, 1500, 2500, 1800,    0,    0,    0,    0, "T6 far right" },
    {   800, -500, 1000, -200,    0,    0,    0,    0, "T7 far above" },
    {  1000, 1700, 1200, 2000,    0,    0,    0,    0, "T8 far below" },
    {  -300, -300,  200,  100,    0,    0,    0,    0, "T9 far TL" },
    {  1800, 1600, 2200, 1700,    0,    0,    0,    0, "T10 far BR" },
    {  -400, 1500,  100, 1800,    0,    0,    0,    0, "T11 far BL" },
    {  1800, -300, 2000,  100,    0,    0,    0,    0, "T12 far TR" },

    /* Crossing from outside into window */
    {   200,  800,  600,  800,  400,  800,  600,  800, "T13 enter left" },
    {  1400,  800, 1800,  800, 1400,  800, 1600,  800, "T14 enter right" },
    {  1000,  100, 1000,  500, 1000,  300, 1000,  500, "T15 enter top" },
    {  1000, 1200, 1000, 1600, 1000, 1200, 1000, 1400, "T16 enter bottom" },

    /* Diagonals crossing the window */
    {   100,  200, 1800, 1700,  381,  300, 1600, 1425, "T17 diag TL-BR" },
    {   100, 1700, 1800,  200,  381, 1400, 1600,  283, "T18 diag BL-TR" },
    {  1800,  200,  100, 1700, 1600,  283,  381, 1400, "T19 diag TR-BL" },
    {  1800, 1700,  100,  200, 1600, 1425,  381,  300, "T20 diag BR-TL" },

    /* Very long lines crossing the world */
    {     0, 1000, 2000, 1000,  400, 1000, 1600, 1000, "T21 horiz world" },
    {  1000,     0, 1000, 2000, 1000,  300, 1000, 1400, "T22 vert world" },
    {     0,     0, 2000, 2000,  400,  400, 1600, 1600, "T23 full world diag" },
    {  2000,     0,     0, 2000, 1600,  400,  400, 1600, "T24 full world diag2" },

    /* Shallow diagonals */
    {   200,  800, 1800,  810,  400,  801, 1600,  809, "T25 shallow up" },
    {   200,  900, 1800,  890,  400,  899, 1600,  891, "T26 shallow down" },

    /* Steep diagonals */
    {  1000,  100, 1010, 1900, 1000,  300, 1001, 1400, "T27 steep right" },
    {   990,  100, 1000, 1900,  999,  300, 1000, 1400, "T28 steep left" },

    /* Long rejects: parallel but far outside */
    {   200,  100,  350,  100,    0,    0,    0,    0, "T29 above parallel" },
    {   200, 1550,  350, 1550,    0,    0,    0,    0, "T30 below parallel" },
    {   100,  800,  100, 1100,    0,    0,    0,    0, "T31 left parallel" },
    {  1800,  800, 1800, 1100,    0,    0,    0,    0, "T32 right parallel" },

    /* Extreme coordinates (within int16 range) */
    {     0, 1000,     0, 1001,    0,    0,    0,    0, "T33 x=0 tiny" },
    {  2000, 1000, 2000, 1001,    0,    0,    0,    0, "T34 x=2000 tiny" },
    {  1000,     0, 1001,     0,    0,    0,    0,    0, "T35 y=0 tiny" },
    {  1000, 2000, 1001, 2000,    0,    0,    0,    0, "T36 y=2000 tiny" },

    /* Lines grazing the clip window edges */
    {   200,  300, 1800,  300,  400,  300, 1600,  300, "T37 graze top edge" },
    {   200, 1400, 1800, 1400,  400, 1400, 1600, 1400, "T38 graze bottom" },
    {   400,  100,  400, 1600,  400,  300,  400, 1400, "T39 graze left edge" },
    {  1600,  100, 1600, 1600, 1600,  300, 1600, 1400, "T40 graze right edge" },

    /* Points on clip window corners */
    {   400,  300,   400,  300,  400,  300,  400,  300, "T41 pt TL corner" },
    {  1600,  300,  1600,  300, 1600,  300, 1600,  300, "T42 pt TR corner" },
    {   400, 1400,   400, 1400,  400, 1400,  400, 1400, "T43 pt BL corner" },
    {  1600, 1400,  1600, 1400, 1600, 1400, 1600, 1400, "T44 pt BR corner" },

    /* Lines connecting two distant corners through the window */
    {     0,     0,  2000, 2000,  400,  400, 1600, 1600, "T45 origin to far BR" },
    {  2000,     0,     0, 2000, 1600,  400,  400, 1600, "T46 far TL to far BR" },

    /* Short lines just outside each edge */
    {   390,  800,   395,  800,    0,    0,    0,    0, "T47 near left edge" },
    {  1605,  800,  1610,  800,    0,    0,    0,    0, "T48 near right edge" },
    {  1000,  295,  1005,  295,    0,    0,    0,    0, "T49 near top edge" },
    {  1000, 1405,  1005, 1405,    0,    0,    0,    0, "T50 near bottom edge" },
};
#define NUM_TESTS (sizeof(tests) / sizeof(tests[0]))

static bool run_test(const TestCase *tc)
{
    int16_t cs_x1 = tc->x1, cs_y1 = tc->y1, cs_x2 = tc->x2, cs_y2 = tc->y2;
    int16_t ci_x1 = tc->x1, ci_y1 = tc->y1, ci_x2 = tc->x2, ci_y2 = tc->y2;
    int16_t el_x1 = tc->x1, el_y1 = tc->y1, el_x2 = tc->x2, el_y2 = tc->y2;

    bool cs_vis = clip_cohen_sutherland(&cs_x1, &cs_y1, &cs_x2, &cs_y2,
                                        CLIP_X1, CLIP_Y1, CLIP_X2, CLIP_Y2);
    bool ci_vis = clip_cohen_sutherland_int16(&ci_x1, &ci_y1, &ci_x2, &ci_y2,
                                              CLIP_X1, CLIP_Y1, CLIP_X2, CLIP_Y2);
    bool el_vis = clip_elite(&el_x1, &el_y1, &el_x2, &el_y2,
                             CLIP_X1, CLIP_Y1, CLIP_X2, CLIP_Y2);

    bool pass = true;

    /* CS-float is the reference. Check visibility agrees. */
    if (ci_vis != cs_vis || el_vis != cs_vis) {
        printf("  VISIBILITY MISMATCH: CS=%d CSi=%d Elite=%d\n",
               cs_vis, ci_vis, el_vis);
        pass = false;
    }

    /* CS-int16 must match CS-float within ±1 pixel. */
    if (cs_vis) {
        if (ci_x1 < cs_x1 - 1 || ci_x1 > cs_x1 + 1 ||
            ci_y1 < cs_y1 - 1 || ci_y1 > cs_y1 + 1 ||
            ci_x2 < cs_x2 - 1 || ci_x2 > cs_x2 + 1 ||
            ci_y2 < cs_y2 - 1 || ci_y2 > cs_y2 + 1) {
            printf("  CSi!=CS: (%d,%d)-(%d,%d) vs (%d,%d)-(%d,%d)\n",
                   ci_x1, ci_y1, ci_x2, ci_y2, cs_x1, cs_y1, cs_x2, cs_y2);
            pass = false;
        }
    }

    /* Elite must match CS within ±2 pixels (integer truncation vs float rounding). */
    if (cs_vis) {
        if (el_x1 < cs_x1 - 2 || el_x1 > cs_x1 + 2 ||
            el_y1 < cs_y1 - 2 || el_y1 > cs_y1 + 2 ||
            el_x2 < cs_x2 - 2 || el_x2 > cs_x2 + 2 ||
            el_y2 < cs_y2 - 2 || el_y2 > cs_y2 + 2) {
            printf("  Elite!=CS: (%d,%d)-(%d,%d) vs (%d,%d)-(%d,%d)\n",
                   el_x1, el_y1, el_x2, el_y2, cs_x1, cs_y1, cs_x2, cs_y2);
            pass = false;
        }
        /* Elite results MUST be strictly inside the clip window. */
        if (el_x1 < CLIP_X1 || el_x1 > CLIP_X2 ||
            el_y1 < CLIP_Y1 || el_y1 > CLIP_Y2 ||
            el_x2 < CLIP_X1 || el_x2 > CLIP_X2 ||
            el_y2 < CLIP_Y1 || el_y2 > CLIP_Y2) {
            printf("  Elite OUTSIDE window: (%d,%d)-(%d,%d) window=(%d,%d)-(%d,%d)\n",
                   el_x1, el_y1, el_x2, el_y2, CLIP_X1, CLIP_Y1, CLIP_X2, CLIP_Y2);
            pass = false;
        }
    }

    printf("%s: %s\n", tc->name, pass ? "PASS" : "FAIL");
    return pass;
}

int main(void)
{
    int passed = 0, failed = 0;

    printf("Line clipping algorithm tests\n");
    printf("Clip window: (%d,%d)-(%d,%d)\n\n", CLIP_X1, CLIP_Y1, CLIP_X2, CLIP_Y2);

    for (int i = 0; i < (int)NUM_TESTS; i++) {
        if (run_test(&tests[i])) {
            passed++;
        } else {
            failed++;
        }
    }

    printf("\n%d passed, %d failed out of %d tests\n",
           passed, failed, (int)NUM_TESTS);

    return failed > 0 ? 1 : 0;
}
