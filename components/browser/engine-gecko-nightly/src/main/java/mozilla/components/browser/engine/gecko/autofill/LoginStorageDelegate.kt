/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.autofill

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.logins.AsyncLoginsStorage
import mozilla.components.service.sync.logins.ServerPassword
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.LoginStorage
import org.mozilla.geckoview.LoginStorage.LoginEntry

/**
 * Defines methods that will be implemented by [LoginStorage.Delegate] in future versions.
 *
 * TODO remove these once the GV API is complete.
 */
internal interface LoginDelegate {
    fun onLoginUsed(login: LoginEntry)
}

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
class LoginStorageDelegate(
    private val loginStorage: AsyncLoginsStorage,
    keyStore: SecureAbove22Preferences,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : LoginDelegate, LoginStorage.Delegate {

    private val password = { scope.async { keyStore.getString(PASSWORDS_KEY)!! } }

    /**
     * Called after a [login] has been autofilled.
     *
     * This is intended for use in telemetry, verification, and similar use cases.
     */
    override fun onLoginUsed(login: LoginEntry) {
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
    override fun onLoginFetch(domain: String): GeckoResult<Array<LoginEntry>>? {
        fun Array<LoginEntry>.toGeckoResult() =
            GeckoResult.fromValue(this)

        return runBlocking {
            // GV expects a synchronous response. Blocking here hasn't caused problems during
            // testing, but we should keep an eye on telemetry
            loginStorage.withUnlocked(password) {
                loginStorage.getByBaseDomain(domain).await()
                    .map { it.toLoginEntry() }
                    .toTypedArray()
                    .toGeckoResult()
            }
        }
    }

    /**
     * Called when a [login] should be saved or updated.
     */
    @Synchronized
    override fun onLoginSave(login: LoginEntry) {
        scope.launch {
            loginStorage.withUnlocked(password) {
                val serverPassword = login.guid?.let { loginStorage.get(it) }?.await()

                when (getPersistenceOperation(login, serverPassword)) {
                    Operation.UPDATE -> {
                        serverPassword?.let { loginStorage.update(it.mergeWithLogin(login)).await() }
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
fun getPersistenceOperation(newLogin: LoginEntry, savedLogin: ServerPassword?): Operation = when {
    newLogin.guid.isNullOrEmpty() || savedLogin == null -> Operation.CREATE
    // This means a password was saved for this site with a blank username. Update that record
    savedLogin.username.isEmpty() -> Operation.UPDATE
    newLogin.username != savedLogin.username -> Operation.CREATE
    else -> Operation.UPDATE
}

/**
 * Will use values from [login] if they are 1) non-null and 2) non-empty.  Otherwise, will fall
 * back to values from [this].
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun ServerPassword.mergeWithLogin(login: LoginEntry): ServerPassword {
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
