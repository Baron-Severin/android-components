/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.autofill

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mozilla.appservices.logins.LoginsStorage
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.logins.AsyncLoginsStorage
import mozilla.components.service.sync.logins.ServerPassword
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.LoginStorage

/**
 * Defines methods that will be implemented by [LoginStorage.Delegate] in future versions.
 *
 * TODO remove these once the GV API is complete.
 */
internal interface LoginDelegate {
    fun onLoginUsed(login: LoginStorage.LoginEntry)
    fun onLoginSave(login: LoginStorage.LoginEntry)
}

/**
 * A type of persistence operation, either 'create' or 'update'.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
enum class Operation { CREATE, UPDATE }

/**
 * [LoginStorage.Delegate] implementation.
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
    private val passwordsKey: () -> Deferred<String>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : LoginDelegate, LoginStorage.Delegate {

    override fun onLoginUsed(login: LoginStorage.LoginEntry) {
        val guid = login.guid
        if (guid == null || guid.isEmpty()) return
        scope.launch {
            loginStorage.withUnlocked(passwordsKey) {
                loginStorage.touch(guid).await()
            }
        }
    }

    override fun onLoginFetch(domain: String): GeckoResult<Array<LoginStorage.LoginEntry>>? {
        fun Array<LoginStorage.LoginEntry>.toGeckoResult() =
            GeckoResult.fromValue(this)

        return runBlocking { // TODO seems like this needs to block.  verify it works
            loginStorage.withUnlocked(passwordsKey) {
                loginStorage.getByHostname(domain).await() // TODO getByHostname -> getByBaseDomain (after AS update)
                    .map { it.toLoginEntry() }
                    .toTypedArray()
                    .toGeckoResult()
            }
        }
    }

    @Synchronized
    override fun onLoginSave(login: LoginStorage.LoginEntry) {
        scope.launch {
            loginStorage.withUnlocked(passwordsKey) {
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
fun getPersistenceOperation(newLogin: LoginStorage.LoginEntry, savedLogin: ServerPassword?): Operation = when {
    newLogin.guid.isNullOrEmpty() || savedLogin == null -> Operation.CREATE
    // This means a password was saved for this site with a blank username. Update that record
    savedLogin.username.isNullOrEmpty() -> Operation.UPDATE
    newLogin.username != savedLogin.username -> Operation.CREATE
    else -> Operation.UPDATE
}

/**
 * Will use values from [login] if they are 1) non-null and 2) non-empty.  Otherwise, will fall
 * back to values from [this].
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun ServerPassword.mergeWithLogin(login: LoginStorage.LoginEntry): ServerPassword {
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
