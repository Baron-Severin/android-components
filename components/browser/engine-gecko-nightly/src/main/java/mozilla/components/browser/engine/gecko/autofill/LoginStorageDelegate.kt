/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.autofill

import androidx.annotation.VisibleForTesting
import mozilla.appservices.logins.LoginsStorage
import mozilla.components.concept.engine.Login
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.logins.ServerPassword
import org.mozilla.geckoview.GeckoResult

// Temporary Interface before lands in GV
internal interface LoginDelegate {
    fun onLoginUsed(login: Login)
    fun onFetchLogins(domain: String): GeckoResult<Array<Login>>
    fun onLoginSave(login: Login)
}

/**
 * [LoginDelegate /* TODO UPDATE */] implementation.
 *
 * When GV needs to update or retrieve information about stored logins, it will call into this
 * class.
 *
 * App will have to instantiate this and set it on the runtime and pass in the [LoginsStorage] and
 * [SecureAbove22Preferences] with a key that conforms to "passwords".  This lets GV delegate to
 * the client for storage.
 */
class LoginStorageDelegate(
    private val loginStorage: LoginsStorage,
    private val keyStore: SecureAbove22Preferences
) : LoginDelegate {
    override fun onLoginUsed(login: Login) {
        val passwordsKey = keyStore.getString(PASSWORDS_KEY)
        val guid = login.guid
        // TODO should this short on an empty string?
        if (passwordsKey == null || guid == null || guid.isEmpty()) return
        with(loginStorage) {
            ensureUnlocked(passwordsKey)
            touch(guid)
            lock()
        }
    }

    override fun onFetchLogins(domain: String): GeckoResult<Array<Login>> {
        val passwordsKey =
            keyStore.getString(PASSWORDS_KEY) ?: return GeckoResult.fromValue(arrayOf())
        loginStorage.ensureUnlocked(passwordsKey).also {
            val result = GeckoResult.fromValue(loginStorage.getByHostname(domain).map { // TODO getByHostname -> getByBaseDomain
                Login(
                    guid = it.id,
                    origin = it.hostname,
                    formActionOrigin = it.formSubmitURL,
                    httpRealm = it.httpRealm,
                    username = it.username ?: "", // TODO this should be nonnull after an incoming AS update
                    password = it.password
                )
            }.toTypedArray())
            loginStorage.lock()
            return result
        }
    }

    // TODO double check that we're locking correctly.  Also, does this need to be synchronized?
    // Request to save or update the given login.
    @Synchronized
    override fun onLoginSave(login: Login) {
        val passwordsKey = keyStore.getString(PASSWORDS_KEY) ?: return // TODO should we fail fast if this is null?
        loginStorage.ensureUnlocked(passwordsKey)
        val guid = login.guid
        val serverPassword = guid?.let { loginStorage.get(it) }

        if (guid != null && serverPassword != null) {
            loginStorage.update(serverPassword.mergeWithLogin(login))
        } else {
            loginStorage.add(
                ServerPassword(
                    id = "", // TODO ask if this is right? Pass empty string if we don't have it?  If so, ask for it to be null
                    username = login.username,
                    password = login.password,
                    hostname = login.origin,
                    formSubmitURL = login.formActionOrigin,
                    httpRealm = login.httpRealm,
                    usernameField = "", // TODO This seems problematic. Run it by Vlad
                    passwordField = ""
                )
            )
        }
        loginStorage.lock()
    }

    companion object {
        const val PASSWORDS_KEY = "passwords"
    }
}

/**
 * Will use values from [login] if they are 1) non-null and 2) non-empty.  Otherwise, will fall
 * back to values from [this].
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun ServerPassword.mergeWithLogin(login: Login): ServerPassword {
    infix fun String?.orUseExisting(other: String?) = if (this?.isNotEmpty() == true) this else other
    infix fun String?.orUseExisting(other: String) = if (this?.isNotEmpty() == true) this else other

    val hostname = login.origin orUseExisting hostname
    val username = login.username orUseExisting username
    val password = login.password orUseExisting password
    val httpRealm = login.httpRealm orUseExisting httpRealm
    val formSubmitUrl = login.formActionOrigin orUseExisting formSubmitURL

    return copy(
        hostname = hostname,
        username = username,
        password = password,
        httpRealm = httpRealm,
        formSubmitURL = formSubmitUrl
    )
}
