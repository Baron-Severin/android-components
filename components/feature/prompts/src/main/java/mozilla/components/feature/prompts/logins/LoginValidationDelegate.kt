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
import mozilla.components.concept.engine.autofill.Login
import mozilla.components.feature.prompts.logins.LoginValidationDelegate.Result
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.logins.AsyncLoginsStorage

/**
 * Provides a method for checking whether or not a given login can be stored.
 */
interface LoginValidationDelegate {

    /**
     * The result of validating a given [Login] against currently stored [Login]s.  This will
     * include whether it can be created, updated, or neither, along with an explanation of any errors.
     */
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
        /**
         * The [Login] cannot be saved.
         */
        sealed class Error : Result() {
            /**
             * The passed [Login] had an empty password field, and so cannot be saved.
             */
            object EmptyPassword : Error()
            /**
             * The [LoginValidationDelegate] has not implemented login functionality, and will
             * always return this error.
             */
            object NotImplemented : Error()
            /**
             * Something went wrong in GeckoView. We have no way to handle this type of error. See
             * [exception] for details.
             */
            data class GeckoError(val exception: InvalidLoginReason) : Error()
        }
    }

    /**
     * Checks whether or not [login] can be persisted.
     *
     * @returns a [LoginValidationDelegate.Result], detailing whether [login] can be saved as a new
     * value, used to update an existing one, or an error occured.
     */
    fun validateCanPersist(login: Login): Deferred<Result>
}

/**
 * Default [LoginValidationDelegate] implementation that always returns false.
 *
 * This can be used by any consumer that does not want to make use of autofill APIs.
 */
class NoopLoginValidationDelegate : LoginValidationDelegate {
    override fun validateCanPersist(login: Login): Deferred<Result> {
        return CompletableDeferred(Result.Error.NotImplemented)
    }
}

/**
 * A delegate that will check against [storage] to see if a given Login can be persisted, and return
 * information about why it can or cannot.
 */
class DefaultLoginValidationDelegate(
    private val storage: AsyncLoginsStorage,
    keyStore: SecureAbove22Preferences,
    private val scope: CoroutineScope = CoroutineScope(IO)
) : LoginValidationDelegate {

    val PASSWORDS_KEY = "passwords" // TODO move (keep in sync w loginstoragedelegate)

    private val password = { scope.async { keyStore.getString(PASSWORDS_KEY)!! } }

    override fun validateCanPersist(login: Login): Deferred<Result> {
        return CoroutineScope(IO).async {
            try {
                storage.ensureUnlocked(password().await()).await()
                // TODO this should share logic from mozilla.components.browser.engine.gecko.autofill.Login.toServerPassword,
                //  but it can't depend on GV. 'service-sync-logins' and 'concept-engine' have no good shared descendant
                //  where this can live, where can it be moved?
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
            } catch (e: InvalidRecordException) {
                when (e.reason) {
                    InvalidLoginReason.DUPLICATE_LOGIN -> Result.CanBeUpdated
                    InvalidLoginReason.EMPTY_PASSWORD -> Result.Error.EmptyPassword
                    InvalidLoginReason.EMPTY_ORIGIN -> Result.Error.GeckoError(e.reason)
                    InvalidLoginReason.BOTH_TARGETS -> Result.Error.GeckoError(e.reason)
                    InvalidLoginReason.NO_TARGET -> Result.Error.GeckoError(e.reason)
                    InvalidLoginReason.ILLEGAL_FIELD_VALUE -> Result.Error.GeckoError(e.reason) // TODO this isnt a gecko error. represent it properly
                }
            } finally {
                @Suppress("DeferredResultUnused") // No action needed
                storage.lock()
            }
        }
    }
}
