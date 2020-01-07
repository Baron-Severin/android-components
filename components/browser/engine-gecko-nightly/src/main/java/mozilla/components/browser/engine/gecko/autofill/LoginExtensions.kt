/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.autofill

import mozilla.appservices.logins.ServerPassword
import mozilla.components.concept.storage.Login
import org.mozilla.geckoview.LoginStorage

/**
 * Converts a GeckoView [LoginStorage.LoginEntry] to an Android Components [Login]
 */
fun LoginStorage.LoginEntry.toLogin() = Login(
    guid = guid,
    origin = origin,
    formActionOrigin = formActionOrigin,
    httpRealm = httpRealm,
    username = username,
    password = password
)

/**
 * Converts an Android Components [Login] to a GeckoView [LoginStorage.LoginEntry]
 */
fun Login.toLoginEntry() = LoginStorage.LoginEntry.Builder()
    .guid(guid)
    .origin(origin)
    .formActionOrigin(formActionOrigin)
    .httpRealm(httpRealm)
    .username(username)
    .password(password)
    .build()
