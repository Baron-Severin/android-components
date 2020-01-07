/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.sync.logins

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import mozilla.appservices.logins.InvalidLoginReason
import mozilla.appservices.logins.InvalidRecordException
import mozilla.appservices.logins.ServerPassword
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginValidationDelegate
import mozilla.components.concept.storage.LoginValidationDelegate.Result
import mozilla.components.lib.dataprotect.SecureAbove22Preferences

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
                    InvalidLoginReason.EMPTY_ORIGIN -> Result.Error.GeckoError(e)
                    InvalidLoginReason.BOTH_TARGETS -> Result.Error.GeckoError(e)
                    InvalidLoginReason.NO_TARGET -> Result.Error.GeckoError(e)
                    InvalidLoginReason.ILLEGAL_FIELD_VALUE -> Result.Error.GeckoError(e) // TODO in what ways can the login fields be illegal? represent these in the UI
                }
            } finally {
                @Suppress("DeferredResultUnused") // No action needed
                storage.lock()
            }
        }
    }
}
