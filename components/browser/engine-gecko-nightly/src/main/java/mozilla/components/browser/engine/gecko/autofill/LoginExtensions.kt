/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.autofill

import mozilla.appservices.logins.ServerPassword
import mozilla.components.concept.engine.autofill.Login
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

/**
 * Converts an Android Components [Login] to an Application Services [ServerPassword]
 */
fun Login.toServerPassword() = ServerPassword(
    // Underlying Rust code will generate a new GUID
    id = "",
    username = username,
    password = password,
    hostname = origin,
    formSubmitURL = formActionOrigin,
    httpRealm = httpRealm,
    // usernameField & passwordField are allowed to be empty when
    // information is not available
    usernameField = "",
    passwordField = ""
)

/**
 * Converts an Application Services [ServerPassword] to an Android Components [Login]
 */
fun ServerPassword.toLogin() = Login(
    guid = id,
    origin = hostname,
    formActionOrigin = formSubmitURL,
    httpRealm = httpRealm,
    username = username,
    password = password
)

/**
 * Converts a GeckoView [LoginStorage.LoginEntry] to an Application Services [ServerPassword]
 */
fun LoginStorage.LoginEntry.toServerPassword() = toLogin().toServerPassword()

/**
 * Converts an Application Services [ServerPassword] to a GeckoView [LoginStorage.LoginEntry]
 */
fun ServerPassword.toLoginEntry() = toLogin().toLoginEntry()
