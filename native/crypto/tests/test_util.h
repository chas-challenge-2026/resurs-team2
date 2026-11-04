/* Minimal shared test helpers for the crypto tests. Valid as both C and C++.
 *
 * Everything is `static`, so each test executable gets its own private
 * failure counter. This assumes one translation unit per executable that
 * calls check(): a second .c/.cpp including this header would count into
 * a separate g_failures that test_summary() never sees.
 */
#ifndef RESURS_TEST_UTIL_H
#define RESURS_TEST_UTIL_H

#include <stdio.h>

static int g_failures = 0;

/* `inline` keeps -Wunused-function quiet in files that don't call a helper. */
static inline void check(int ok, const char *name)
{
    printf("%s %s\n", ok ? "[PASS]" : "[FAIL]", name);
    if (!ok)
    {
        g_failures++;
    }
}

/* Prints the failure count and returns the process exit code for main(). */
static inline int test_summary(void)
{
    printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}

#endif /* RESURS_TEST_UTIL_H */
