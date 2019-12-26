/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.concept.engine.autofill

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * Represents a login that can be used by autofill APIs.
 *
 * Note that much of this information can be partial (e.g., a user might save a password with a
 * blank username).
 */
@Parcelize
data class Login(
    /**
     * The unique identifier for this login entry.
     */
    val guid: String? = null,
    /**
     * The origin this login entry applies to.
     */
    val origin: String,
    /**
     * The origin this login entry was submitted to.
     * This only applies to form-based login entries.
     * It's derived from the action attribute set on the form element.
     */
    val formActionOrigin: String? = null,
    /**
     * The HTTP realm this login entry was requested for.
     * This only applies to non-form-based login entries.
     * It's derived from the WWW-Authenticate header set in a HTTP 401
     * response, see RFC2617 for details.
     */
    val httpRealm: String? = null,
    /**
     * The username for this login entry.
     */
    val username: String,
    /**
     * The password for this login entry.
     */
    val password: String
) : Parcelable
