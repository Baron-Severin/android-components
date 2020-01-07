/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.sync.logins

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginStorageDelegate
import mozilla.components.lib.dataprotect.SecureAbove22Preferences

/**
 * A type of persistence operation, either 'create' or 'update'.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
enum class Operation { CREATE, UPDATE }

private const val PASSWORDS_KEY = "passwords"

/**
 * [LoginStorage.Delegate] implementation.
 *
 * An abstraction that handles the persistence and retrieval of [LoginEntry]s so that Gecko doesn't
 * have to.
 *
 * In order to use this class, attach it to the active [GeckoRuntime] as its `loginStorageDelegate`.
 * It is not designed to work with other engines.
 */
class DefaultLoginStorageDelegate(
    private val loginStorage: AsyncLoginsStorage,
    keyStore: SecureAbove22Preferences,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : LoginStorageDelegate {

    private val password = { scope.async { keyStore.getString(PASSWORDS_KEY)!! } }

    /**
     * Called after a [login] has been autofilled.
     *
     * This is intended for use in telemetry, verification, and similar use cases.
     */
    override fun onLoginUsed(login: Login) {
        val guid = login.guid
        if (guid == null || guid.isEmpty()) return
        scope.launch {
            loginStorage.withUnlocked(password) {
                loginStorage.touch(guid).await()
            }
        }
    }

    /**
     * Given a [domain], returns a [GeckoResult] of the matching [LoginEntry]s found in
     * [loginStorage].
     *
     * This is called when Gecko believes a field should be autofilled.
     */
    override fun onLoginFetch(domain: String): List<Login> {
        return runBlocking {
            // GV expects a synchronous response. Blocking here hasn't caused problems during
            // testing, but we should keep an eye on telemetry
            loginStorage.withUnlocked(password) {
                loginStorage.getByBaseDomain(domain).await()
                    .map { it.toLogin() }
            }
        }
    }

    /**
     * Called when a [login] should be saved or updated.
     */
    @Synchronized
    override fun onLoginSave(login: Login) {
        scope.launch {
            loginStorage.withUnlocked(password) {
                val existingLogin = login.guid?.let { loginStorage.get(it) }?.await()

                when (getPersistenceOperation(login, existingLogin)) {
                    Operation.UPDATE -> {
                        existingLogin?.let { loginStorage.update(it.mergeWithLogin(login)).await() }
                    }
                    Operation.CREATE -> {
                        loginStorage.add(login.toServerPassword()).await()
                    }
                }
            }
        }
    }
}

/**
 * Returns whether an existing login record should be [UPDATE]d or a new one [CREATE]d, based
 * on the saved [ServerPassword] and new [Login].
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun getPersistenceOperation(newLogin: Login, savedLogin: ServerPassword?): Operation = when {
    newLogin.guid.isNullOrEmpty() || savedLogin == null -> Operation.CREATE
    // This means a password was saved for this site with a blank username. Update that record
    savedLogin.username.isEmpty() -> Operation.UPDATE
    newLogin.username != savedLogin.username -> Operation.CREATE
    else -> Operation.UPDATE
}

/**
 * Will use values from [this] if they are 1) non-null and 2) non-empty.  Otherwise, will fall
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
