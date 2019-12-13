package mozilla.components.browser.engine.gecko.autofill

import mozilla.appservices.logins.LoginsStorage
import mozilla.components.browser.engine.gecko.autofill.LoginStorageDelegate.Companion.PASSWORDS_KEY
import mozilla.components.concept.engine.Login
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.logins.ServerPassword
import mozilla.components.support.test.anyNonNull
import mozilla.components.support.test.mock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class LoginStorageDelegateTest {

    @Mock private lateinit var keystore: SecureAbove22Preferences
    private lateinit var loginsStorage: LoginsStorage
    private lateinit var delegate: LoginStorageDelegate

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        loginsStorage = mockLoginsStorage()
        delegate = LoginStorageDelegate(loginsStorage, keystore)
    }

    @Test
    fun `WHEN onLoginUsed is passed null values THEN loginStorage should be locked`() {
        mockSavedKey(false)
        var login = createLogin("guid")

        delegate.onLoginUsed(login)
        assertTrue(loginsStorage.isLocked())

        mockSavedKey(true)
        login = createLogin(null)

        delegate.onLoginUsed(login)
        assertTrue(loginsStorage.isLocked())

        mockSavedKey(true)
        login = createLogin(guid = "") // should this short on an empty string?

        delegate.onLoginUsed(login)
        assertTrue(loginsStorage.isLocked())
    }

    @Test
    fun `WHEN onLoginUsed is passed null values THEN loginStorage should not be touched`() {
        `when`(keystore.getString(PASSWORDS_KEY)).thenReturn(null)
        var login = createLogin("guid")

        delegate.onLoginUsed(login)
        verify(loginsStorage, times(0)).touch(anyNonNull())

        mockSavedKey(true)
        login = createLogin(null)

        delegate.onLoginUsed(login)
        verify(loginsStorage, times(0)).touch(anyNonNull())

        mockSavedKey(true)
        login = createLogin(guid = "") // should this short on an empty string?

        delegate.onLoginUsed(login)
        verify(loginsStorage, times(0)).touch(anyNonNull())
    }

    @Test
    fun `WHEN onLoginsUsed is passed good values THEN loginStorage should be touched`() {
        mockSavedKey(true)
        val login = createLogin("guid")

        delegate.onLoginUsed(login)
        verify(loginsStorage, times(1)).touch(anyNonNull())
    }

    @Test
    fun `WHEN onLoginsUsed is passed good values THEN loginStorage should be locked`() {
        mockSavedKey(true)
        val login = createLogin("guid")

        delegate.onLoginUsed(login)
        assertTrue(loginsStorage.isLocked())
    }

    @Test
    fun `GIVEN keystore pass is invalid WHEN onLoginSave loginStorage should be unchanged`() {
        mockSavedKey(false)
        val login = createLogin(guid = "guid")

        delegate.onLoginSave(login)

        verify(loginsStorage, times(0)).update(anyNonNull())
        verify(loginsStorage, times(0)).add(anyNonNull())
    }

    @Test
    fun `GIVEN guid is null WHEN onLoginSave THEN a new login should be added`() {
        mockSavedKey(true)
        val login = createLogin(guid = null)

        delegate.onLoginSave(login)

        verify(loginsStorage, times(0)).update(anyNonNull())
        verify(loginsStorage, times(1)).add(anyNonNull())
    }

    @Test
    fun `GIVEN guid is nonnull AND no password is associated with that guid WHEN onLoginSave THEN a new login should be added`() {
        mockSavedKey(true)
        val login = createLogin(guid = "guid")
        `when`(loginsStorage.get(login.guid!!)).thenReturn(null as ServerPassword?)

        delegate.onLoginSave(login)

        verify(loginsStorage, times(0)).update(anyNonNull())
        verify(loginsStorage, times(1)).add(anyNonNull())
    }

    @Test
    fun `GIVEN guid is nonnull AND guid is associated with a ServerPassword WHEN onLoginSave THEN update should be called`() {
        mockSavedKey(true)
        val login = createLogin(guid = "guid")
        val serverPassword = ServerPassword("", "", "", "")
        `when`(loginsStorage.get(login.guid!!)).thenReturn(serverPassword)

        delegate.onLoginSave(login)

        verify(loginsStorage, times(1)).update(anyNonNull())
        verify(loginsStorage, times(0)).add(anyNonNull())
    }

    @Test
    fun `GIVEN login is non-null, non-empty WHEN mergeWithLogin THEN result should use values from login`() {
        val login = Login(
            guid = "guid",
            origin = "origin",
            formActionOrigin = "fao",
            httpRealm = "httpRealm",
            username = "username",
            password = "password"
        )
        val serverPassword = ServerPassword(
            id = "spId",
            hostname = "spHost",
            username = "spUser",
            password = "spPassword",
            httpRealm = "spHttpRealm",
            formSubmitURL = "spFormSubmitUrl"
        )

        val expected = ServerPassword(
            id = "spId",
            hostname = "origin",
            username = "username",
            password = "password",
            httpRealm = "httpRealm",
            formSubmitURL = "fao"
        )

        assertEquals(expected, serverPassword.mergeWithLogin(login))
    }

    @Test
    fun `GIVEN login has null values WHEN mergeWithLogin THEN those values should be taken from serverPassword`() {
        val login = Login(
            guid = null,
            origin = "origin",
            formActionOrigin = null,
            httpRealm = null,
            username = "username",
            password = "password"
        )
        val serverPassword = ServerPassword(
            id = "spId",
            hostname = "spHost",
            username = "spUser",
            password = "spPassword",
            httpRealm = "spHttpRealm",
            formSubmitURL = "spFormSubmitUrl"
        )

        val expected = ServerPassword(
            id = "spId",
            hostname = "origin",
            username = "username",
            password = "password",
            httpRealm = "spHttpRealm",
            formSubmitURL = "spFormSubmitUrl"
        )

        assertEquals(expected, serverPassword.mergeWithLogin(login))
    }

    @Test
    fun `GIVEN login has empty values WHEN mergeWithLogin THEN those values should be taken from serverPassword`() {
        val login = Login(
            guid = "",
            origin = "",
            formActionOrigin = "",
            httpRealm = "",
            username = "",
            password = ""
        )
        val serverPassword = ServerPassword(
            id = "spId",
            hostname = "spHost",
            username = "spUser",
            password = "spPassword",
            httpRealm = "spHttpRealm",
            formSubmitURL = "spFormSubmitUrl"
        )

        val expected = ServerPassword(
            id = "spId",
            hostname = "spHost",
            username = "spUser",
            password = "spPassword",
            httpRealm = "spHttpRealm",
            formSubmitURL = "spFormSubmitUrl"
        )

        assertEquals(expected, serverPassword.mergeWithLogin(login))
    }

    private fun mockSavedKey(validKeySaved: Boolean) {
        val saved = if (validKeySaved) PASSWORDS_KEY else null
        `when`(keystore.getString(PASSWORDS_KEY)).thenReturn(saved)
    }

}

fun mockLoginsStorage(): LoginsStorage {
    val loginsStorage = mock<LoginsStorage>()
    var isLocked = true

    fun <T> setLockedWhen(on: T, newIsLocked: Boolean) {
        `when`(on).thenAnswer {
            isLocked = newIsLocked
            Unit
        }
    }

    setLockedWhen(loginsStorage.ensureLocked(), true)
    setLockedWhen(loginsStorage.lock(), true)

    setLockedWhen(loginsStorage.ensureUnlocked(anyNonNull<ByteArray>()), false)
    setLockedWhen(loginsStorage.ensureUnlocked(anyNonNull<String>()), false)
    setLockedWhen(loginsStorage.unlock(anyNonNull<String>()), false)
    setLockedWhen(loginsStorage.unlock(anyNonNull<ByteArray>()), false)

    `when`(loginsStorage.isLocked()).thenAnswer { isLocked }
    `when`(loginsStorage.touch(anyNonNull())).thenAnswer { }

    return loginsStorage
}

fun createLogin(guid: String?) = Login(
    guid = guid,
    username = "username",
    password = "password",
    origin = "origin"
)
