/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.support.test

import org.mockito.Mockito

/**
 * Mockito matcher that matches <strong>anything</strong>, including nulls and varargs.
 *
 * (The version from Mockito doesn't work correctly with Kotlin code.)
 */
fun <T> any(): T {
    Mockito.any<T>()
    return uninitialized()
}

/**
 * Matches anything that is non-null: use this when [Mockito.any] crashes.
 *
 * A normal [Mockito.any] is a nullable type, so this allows us to test non-null
 * code.
 *
 * Taken from https://medium.com/@elye.project/befriending-kotlin-and-mockito-1c2e7b0ef791
 */
@Suppress("UNCHECKED_CAST")
fun <T> anyNonNull(): T {
    // Internally, this calls static void method reportMatcher, which seems to
    // set some class state.  If this line is commented out, the function will
    // not work
    Mockito.any<T>()
    return null as T
}

/**
 * Mockito matcher that matches if the argument is the same as the provided value.
 *
 * (The version from Mockito doesn't work correctly with Kotlin code.)
 */
fun <T> eq(value: T): T {
    return Mockito.eq(value) ?: value
}

@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T
