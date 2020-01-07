/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.autofill

import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginStorageDelegate
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.LoginStorage

/**
 * TODO
 */
class GeckoLoginDelegateWrapper(private val storageDelegate: LoginStorageDelegate): LoginStorage.Delegate {
    override fun onLoginSave(login: LoginStorage.LoginEntry) {
        storageDelegate.onLoginSave(login.toLogin())
    }

    override fun onLoginFetch(domain: String): GeckoResult<Array<LoginStorage.LoginEntry>>? {
        val storedLogins = storageDelegate.onLoginFetch(domain)

        return storedLogins
            .map { it.toLoginEntry() }
            .toTypedArray()
            .let { GeckoResult.fromValue(it) }
    }

    /**
     * TODO not yet in GV
     */
    fun onLoginUsed(login: LoginStorage.LoginEntry) {
        storageDelegate.onLoginUsed(login.toLogin())
    }
}

/**
 * Converts a GeckoView [LoginStorage.LoginEntry] to an Android Components [Login]
 */
private fun LoginStorage.LoginEntry.toLogin() = Login(
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
private fun Login.toLoginEntry() = LoginStorage.LoginEntry.Builder()
    .guid(guid)
    .origin(origin)
    .formActionOrigin(formActionOrigin)
    .httpRealm(httpRealm)
    .username(username)
    .password(password)
    .build()
