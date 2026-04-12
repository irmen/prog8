/* lineclip.h - Line clipping algorithms (Cohen-Sutherland, Liang-Barsky, Elite-inspired) */
#ifndef LINECLIP_H
#define LINECLIP_H

#include <stdint.h>
#include <stdbool.h>

/* All coordinates are signed to support off-screen positions */

/* Cohen-Sutherland line clipping (float intersection math) */
bool clip_cohen_sutherland(int16_t *x1, int16_t *y1, int16_t *x2, int16_t *y2,
                           int16_t clipx1, int16_t clipy1, int16_t clipx2, int16_t clipy2);

/* Cohen-Sutherland line clipping (pure int16_t, uses frac_mul internally) */
bool clip_cohen_sutherland_int16(int16_t *x1, int16_t *y1, int16_t *x2, int16_t *y2,
                                 int16_t clipx1, int16_t clipy1, int16_t clipx2, int16_t clipy2);

/* Elite-inspired two-stage clipping (BBC Micro, 6502) */
/* Based on: https://elite.bbcelite.com/deep_dives/line-clipping.html */
bool clip_elite(int16_t *x1, int16_t *y1, int16_t *x2, int16_t *y2,
                int16_t clipx1, int16_t clipy1, int16_t clipx2, int16_t clipy2);

#endif /* LINECLIP_H */
