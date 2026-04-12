/* clip_visual.c - SDL3 visual test for line clipping algorithms
 *
 * Draws the clip window and all test cases.
 * Gray = original line. Green = clipped result (visible). Red = rejected.
 * Press SPACE to cycle algorithms. Press Q to quit.
 *
 * Build: gcc -Wall -Wextra -O2 -o clip_visual clip_visual.c lineclip.c -lSDL3
 */
#include <SDL3/SDL.h>
#include <SDL3/SDL_main.h>
#include <stdint.h>
#include "lineclip.h"

/* ----------------------------------------------------------------------- */

#define WIDTH  1200
#define HEIGHT 900

/* World coordinate range: 0..2000 mapped to screen */
#define WORLD_MIN 0
#define WORLD_MAX 2000

/* Clip window: (400,300) to (1600,1400) */
#define CLIP_X1 400
#define CLIP_Y1 300
#define CLIP_X2 1600
#define CLIP_Y2 1400

/* Map world coords to screen coords */
static float sx(int16_t x) {
    return 80.0f + ((float)(x - WORLD_MIN) * (float)(WIDTH - 160) / (float)(WORLD_MAX - WORLD_MIN));
}
static float sy(int16_t y) {
    return (float)(HEIGHT - 80) - ((float)(y - WORLD_MIN) * (float)(HEIGHT - 160) / (float)(WORLD_MAX - WORLD_MIN));
}

typedef struct {
    int16_t x1, y1, x2, y2;
    const char *name;
} TestCase;

static const TestCase tests[] = {
    /* Inside the clip window */
    {  800,  600, 1200, 1000, "T1 inside" },
    {  420,  320,  500,  400, "T2 near TL" },
    { 1580, 1380, 1600, 1400, "T3 near BR" },
    { 1000,  850, 1000,  850, "T4 point inside" },

    /* Completely outside -- far away */
    {  -500, -200, -300,  100, "T5 far left" },
    {  2200, 1500, 2500, 1800, "T6 far right" },
    {   800, -500, 1000, -200, "T7 far above" },
    {  1000, 1700, 1200, 2000, "T8 far below" },
    {  -300, -300,  200,  100, "T9 far TL" },
    {  1800, 1600, 2200, 1700, "T10 far BR" },
    {  -400, 1500,  100, 1800, "T11 far BL" },
    {  1800, -300, 2000,  100, "T12 far TR" },

    /* Crossing from outside into window */
    {   200,  800,  600,  800, "T13 enter left" },
    {  1400,  800, 1800,  800, "T14 enter right" },
    {  1000,  100, 1000,  500, "T15 enter top" },
    {  1000, 1200, 1000, 1600, "T16 enter bottom" },

    /* Diagonals crossing the window */
    {   100,  200, 1800, 1700, "T17 diag TL-BR" },
    {   100, 1700, 1800,  200, "T18 diag BL-TR" },
    {  1800,  200,  100, 1700, "T19 diag TR-BL" },
    {  1800, 1700,  100,  200, "T20 diag BR-TL" },

    /* Very long lines crossing the world */
    {     0, 1000, 2000, 1000, "T21 horiz world" },
    {  1000,     0, 1000, 2000, "T22 vert world" },
    {     0,     0, 2000, 2000, "T23 full world diag" },
    {  2000,     0,     0, 2000, "T24 full world diag2" },

    /* Shallow diagonals */
    {   200,  800, 1800,  810, "T25 shallow up" },
    {   200,  900, 1800,  890, "T26 shallow down" },

    /* Steep diagonals */
    {  1000,  100, 1010, 1900, "T27 steep right" },
    {   990,  100, 1000, 1900, "T28 steep left" },

    /* Long rejects: parallel but far outside */
    {   200,  100,  350,  100, "T29 above parallel" },
    {   200, 1550,  350, 1550, "T30 below parallel" },
    {   100,  800,  100, 1100, "T31 left parallel" },
    {  1800,  800, 1800, 1100, "T32 right parallel" },

    /* Extreme coordinates (within int16 range) */
    {     0, 1000,     0, 1001, "T33 x=0 tiny" },
    {  2000, 1000, 2000, 1001, "T34 x=2000 tiny" },
    {  1000,     0, 1001,     0, "T35 y=0 tiny" },
    {  1000, 2000, 1001, 2000, "T36 y=2000 tiny" },

    /* Lines grazing the clip window edges */
    {   200,  300, 1800,  300, "T37 graze top edge" },
    {   200, 1400, 1800, 1400, "T38 graze bottom" },
    {   400,  100,  400, 1600, "T39 graze left edge" },
    {  1600,  100, 1600, 1600, "T40 graze right edge" },

    /* Points on clip window corners */
    {   400,  300,   400,  300, "T41 pt TL corner" },
    {  1600,  300,  1600,  300, "T42 pt TR corner" },
    {   400, 1400,   400, 1400, "T43 pt BL corner" },
    {  1600, 1400,  1600, 1400, "T44 pt BR corner" },

    /* Lines connecting two distant corners through the window */
    {     0,     0,  2000, 2000, "T45 origin to far BR" },
    {  2000,     0,     0, 2000, "T46 far TL to far BR" },

    /* Short lines just outside each edge */
    {   390,  800,   395,  800, "T47 near left edge" },
    {  1605,  800,  1610,  800, "T48 near right edge" },
    {  1000,  295,  1005,  295, "T49 near top edge" },
    {  1000, 1405,  1005, 1405, "T50 near bottom edge" },
};
#define NUM_TESTS (sizeof(tests) / sizeof(tests[0]))

typedef struct {
    const char *name;
    bool (*clip)(int16_t*, int16_t*, int16_t*, int16_t*,
                 int16_t, int16_t, int16_t, int16_t);
} Algo;

static const Algo algos[] = {
    { "CS-float",          clip_cohen_sutherland },
    { "CS-int16",          clip_cohen_sutherland_int16 },
    { "Elite-style",       clip_elite },
};
#define NUM_ALGOS (sizeof(algos) / sizeof(algos[0]))

/* Draw a line */
static void draw_line(SDL_Renderer *r, float x1, float y1, float x2, float y2,
                      SDL_FColor c)
{
    SDL_FPoint pts[2] = { { x1, y1 }, { x2, y2 } };
    SDL_SetRenderDrawColorFloat(r, c.r, c.g, c.b, c.a);
    SDL_RenderLines(r, pts, 2);
}

/* ----------------------------------------------------------------------- */

int main(void)
{
    SDL_Window *win;
    SDL_Renderer *ren;
    SDL_Event ev;
    int algo_idx = 0;
    int quit = 0;

    if (!SDL_Init(SDL_INIT_VIDEO)) {
        SDL_Log("SDL_Init failed: %s", SDL_GetError());
        return 1;
    }

    win = SDL_CreateWindow("Line Clipping Visual Test", WIDTH, HEIGHT, 0);
    ren = SDL_CreateRenderer(win, NULL);
    if (!win || !ren) {
        SDL_Log("Window/Renderer failed: %s", SDL_GetError());
        return 1;
    }

    /* Clip window rect */
    SDL_FRect clip_rect = {
        sx(CLIP_X1), sy(CLIP_Y2),
        sx(CLIP_X2) - sx(CLIP_X1),
        sy(CLIP_Y1) - sy(CLIP_Y2)
    };

    /* Grid lines */
    SDL_SetRenderDrawColor(ren, 50, 50, 60, 255);
    for (int i = 0; i <= 2000; i += 200) {
        SDL_RenderLine(ren, sx(i), sy(WORLD_MIN), sx(i), sy(WORLD_MAX));
        SDL_RenderLine(ren, sx(WORLD_MIN), sy(i), sx(WORLD_MAX), sy(i));
    }

    while (!quit) {
        while (SDL_PollEvent(&ev)) {
            if (ev.type == SDL_EVENT_QUIT) quit = 1;
            if (ev.type == SDL_EVENT_KEY_DOWN) {
                switch (ev.key.key) {
                case SDLK_ESCAPE: case SDLK_Q: quit = 1; break;
                case SDLK_SPACE:
                    algo_idx = (algo_idx + 1) % NUM_ALGOS;
                    break;
                }
            }
        }

        /* Clear */
        SDL_SetRenderDrawColor(ren, 15, 15, 25, 255);
        SDL_RenderClear(ren);

        /* Grid */
        SDL_SetRenderDrawColor(ren, 40, 40, 55, 255);
        for (int i = 0; i <= WORLD_MAX; i += 200) {
            SDL_RenderLine(ren, sx(i), sy(WORLD_MIN), sx(i), sy(WORLD_MAX));
            SDL_RenderLine(ren, sx(WORLD_MIN), sy(i), sx(WORLD_MAX), sy(i));
        }

        /* Fill clip window */
        SDL_FColor fill = { 0.1f, 0.12f, 0.18f, 0.7f };
        SDL_SetRenderDrawColorFloat(ren, fill.r, fill.g, fill.b, fill.a);
        SDL_RenderFillRect(ren, &clip_rect);

        /* Draw clip window border */
        SDL_SetRenderDrawColor(ren, 100, 100, 130, 255);
        SDL_RenderRect(ren, &clip_rect);

        const Algo *algo = &algos[algo_idx];

        /* Draw every test */
        for (int i = 0; i < (int)NUM_TESTS; i++) {
            int16_t ox1 = tests[i].x1, oy1 = tests[i].y1;
            int16_t ox2 = tests[i].x2, oy2 = tests[i].y2;
            int16_t rx1 = ox1, ry1 = oy1, rx2 = ox2, ry2 = oy2;

            int vis = algo->clip(&rx1, &ry1, &rx2, &ry2,
                                 CLIP_X1, CLIP_Y1, CLIP_X2, CLIP_Y2);

            /* Original line in dim gray */
            SDL_FColor gray = { 0.3f, 0.3f, 0.35f, 0.6f };
            draw_line(ren, sx(ox1), sy(oy1), sx(ox2), sy(oy2), gray);

            if (vis) {
                /* Clipped result in green */
                SDL_FColor green = { 0.15f, 0.9f, 0.3f, 1.0f };
                draw_line(ren, sx(rx1), sy(ry1), sx(rx2), sy(ry2), green);

                /* Endpoint markers */
                SDL_FColor marker = { 0.3f, 1.0f, 0.4f, 1.0f };
                SDL_FRect m = { sx(rx1)-3, sy(ry1)-3, 6, 6 };
                SDL_SetRenderDrawColorFloat(ren, marker.r, marker.g, marker.b, marker.a);
                SDL_RenderFillRect(ren, &m);
                m.x = sx(rx2)-3; m.y = sy(ry2)-3;
                SDL_RenderFillRect(ren, &m);
            } else {
                /* Rejected: red X at midpoint */
                SDL_FColor red = { 0.8f, 0.15f, 0.15f, 1.0f };
                float mx = (sx(ox1) + sx(ox2)) / 2;
                float my = (sy(oy1) + sy(oy2)) / 2;
                SDL_SetRenderDrawColorFloat(ren, red.r, red.g, red.b, red.a);
                SDL_FPoint xa[2] = { { mx-5, my-5 }, { mx+5, my+5 } };
                SDL_FPoint xb[2] = { { mx-5, my+5 }, { mx+5, my-5 } };
                SDL_RenderLines(ren, xa, 2);
                SDL_RenderLines(ren, xb, 2);
            }
        }

        /* Algorithm name in title bar */
        char title[128];
        SDL_snprintf(title, sizeof(title), "%s (%d tests)", algo->name, (int)NUM_TESTS);
        SDL_SetWindowTitle(win, title);

        SDL_RenderPresent(ren);
    }

    SDL_DestroyRenderer(ren);
    SDL_DestroyWindow(win);
    SDL_Quit();
    return 0;
}
