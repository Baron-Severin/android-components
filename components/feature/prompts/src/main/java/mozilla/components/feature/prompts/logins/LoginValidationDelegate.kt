/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.prompts.logins

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import mozilla.appservices.logins.InvalidLoginReason
import mozilla.appservices.logins.InvalidRecordException
import mozilla.appservices.logins.ServerPassword
import mozilla.components.concept.engine.Login
import mozilla.components.feature.prompts.logins.LoginValidationDelegate.Result
import mozilla.components.service.sync.logins.AsyncLoginsStorage

/**
 * Provides a method for checking whether or not a given login can be stored.
 */
interface LoginValidationDelegate {

    sealed class Result {
        /**
         * Indicates that the [Login] does not currently exist in the storage, and a new entry
         * with its information can be made.
         */
        object CanBeCreated : Result()
        /**
         * Indicates that a matching [Login] was found in storage, and the [Login] can be used
         * to update its information.
         */
        object CanBeUpdated : Result()
        sealed class Error : Result() {
            /**
             * The passed [Login] had an empty password field, and so cannot be saved.
             */
            object EmptyPassword : Error()
            object NotImplemented : Error()
            data class GeckoError(val exception: InvalidLoginReason) : Error()
        }
    }

    fun validateCanPersist(login: Login): Deferred<Result>
}

/**
 * Default [LoginValidationDelegate] implementation that returns false.
 *
 * This can be used by any consumer that does not want to make use of autofill APIs.
 */
class NoopLoginValidationDelegate : LoginValidationDelegate {
    override fun validateCanPersist(login: Login): Deferred<Result> {
        return CompletableDeferred(Result.Error.NotImplemented)
    }
}

/**
 * TODO
 */
class DefaultLoginValidationDelegate(
    private val storage: AsyncLoginsStorage,
    private val passwordsKey: String
) : LoginValidationDelegate {
    override fun validateCanPersist(login: Login): Deferred<Result> {
        try {
            return CoroutineScope(IO).async {
                storage.ensureUnlocked(passwordsKey).await()
                // TODO share logic for login -> server password
                storage.ensureValid(ServerPassword(
                    id = login.guid ?: "",
                    username = login.username,
                    password = login.password,
                    hostname = login.origin,
                    formSubmitURL = login.formActionOrigin,
                    httpRealm = login.httpRealm,
                    usernameField = "",
                    passwordField = ""
                )).await()
                Result.CanBeCreated
            }
        } catch (e: InvalidRecordException) {
            return CoroutineScope(IO).async {
                when (e.reason) {
                    InvalidLoginReason.DUPLICATE_LOGIN -> Result.CanBeUpdated
                    InvalidLoginReason.EMPTY_PASSWORD -> Result.Error.EmptyPassword
                    InvalidLoginReason.EMPTY_ORIGIN -> Result.Error.GeckoError(e.reason)
                    InvalidLoginReason.BOTH_TARGETS -> Result.Error.GeckoError(e.reason)
                    InvalidLoginReason.NO_TARGET -> Result.Error.GeckoError(e.reason)
                }
            }
        } finally {
            @Suppress("DeferredResultUnused") // No action needed
            storage.lock()
        }
    }
}