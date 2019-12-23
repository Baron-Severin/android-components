/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.autofill

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mozilla.appservices.logins.LoginsStorage
import mozilla.components.concept.engine.Login
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.logins.AsyncLoginsStorage
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
    private val loginStorage: AsyncLoginsStorage,
//    private val keyStore: SecureAbove22Preferences
    private val passwordsKey: String
) : LoginDelegate {
    override fun onLoginUsed(login: Login) {
//        val passwordsKey = keyStore.getString(PASSWORDS_KEY)
        val guid = login.guid
        if (guid == null || guid.isEmpty()) return
        with(loginStorage) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ensureUnlocked(passwordsKey).await()
                    touch(guid).await()
                } finally {
                    @Suppress("DeferredResultUnused") // No action needed
                    lock()
                }
            }
        }
    }

    override fun onFetchLogins(domain: String): GeckoResult<Array<Login>> {
        return runBlocking { // TODO doesn't seem like we have any other option here.  verify it works
            try {
                loginStorage.ensureUnlocked(passwordsKey).await()
                GeckoResult.fromValue(loginStorage.getByHostname(domain).await().map { // TODO getByHostname -> getByBaseDomain
                    Login(
                        guid = it.id,
                        origin = it.hostname,
                        formActionOrigin = it.formSubmitURL,
                        httpRealm = it.httpRealm,
                        username = it.username ?: "", // TODO this should be nonnull after an incoming AS update
                        password = it.password
                    )
                }.toTypedArray())
            } finally {
                @Suppress("DeferredResultUnused") // No action needed
                loginStorage.lock()
            }

        }
    }

    @Synchronized
    override fun onLoginSave(login: Login) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                loginStorage.ensureUnlocked(passwordsKey).await()
                val guid = login.guid
                val serverPassword = guid?.let { loginStorage.get(it) }?.await()

                /*
                TODO handle (at least) 4 cases here

                - update an existing record with a guid
                - save a new record (no guid)
                - save a new record (has a guid, but user changed the username, so it's a new login now)
                - update an existing record with a guid, which had an empty username, and user added a username
                 */

                if (guid != null && serverPassword != null) {
                    loginStorage.update(serverPassword.mergeWithLogin(login)).await()
                } else {
                    loginStorage.add(
                        ServerPassword(
                            // Underlying Rust code will generate a new GUID
                            id = "",
                            username = login.username,
                            password = login.password,
                            hostname = login.origin,
                            formSubmitURL = login.formActionOrigin,
                            httpRealm = login.httpRealm,
                            // These two fields are allowed to be empty when information is not available
                            usernameField = "",
                            passwordField = ""
                        )
                    ).await()
                }
            } finally {
                @Suppress("DeferredResultUnused") // No action needed
                loginStorage.lock()
            }
        }
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
