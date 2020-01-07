package mozilla.components.browser.engine.gecko.autofill

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import mozilla.components.concept.engine.Login
import mozilla.components.service.sync.logins.AsyncLoginsStorage
import mozilla.components.service.sync.logins.ServerPassword
import mozilla.components.support.test.anyNonNull
import mozilla.components.support.test.mock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class DefaultLoginStorageDelegateTest {

    private lateinit var loginsStorage: AsyncLoginsStorage
    private lateinit var delegateDefault: DefaultLoginStorageDelegate
    private lateinit var scope: TestCoroutineScope

    @Before
    fun before() {
        loginsStorage = mockLoginsStorage()
        scope = TestCoroutineScope()
        delegateDefault = DefaultLoginStorageDelegate(loginsStorage, "password", scope)
    }

    @Test
    fun `WHEN onLoginsUsed is used THEN loginStorage should be touched`() {
        scope.launch {
            val login = createLogin("guid")

            delegateDefault.onLoginUsed(login)
            verify(loginsStorage, times(1)).touch(anyNonNull()).await()
        }
    }

    @Test
    fun `WHEN guid is null or empty THEN should create a new record`() {
        val serverPassword = createServerPassword()

        val fromNull = getPersistenceOperation(createLogin(guid = null), serverPassword)
        val fromEmpty = getPersistenceOperation(createLogin(guid = ""), serverPassword)

        assertEquals(Operation.CREATE, fromNull)
        assertEquals(Operation.CREATE, fromEmpty)
    }

    @Test
    fun `WHEN guid matches existing record AND saved record has an empty username THEN should update existing record`() {
        val serverPassword = createServerPassword(id = "1", username = "")
        val login = createLogin(guid = "1")

        assertEquals(Operation.UPDATE, getPersistenceOperation(login, serverPassword))
    }

    @Test
    fun `WHEN guid matches existing record AND new username is different from saved THEN should create new record`() {
        val serverPassword = createServerPassword(id = "1", username = "old")
        val login = createLogin(guid = "1", username = "new")

        assertEquals(Operation.CREATE, getPersistenceOperation(login, serverPassword))
    }

    @Test
    fun `WHEN guid and username match THEN update existing record`() {
        val serverPassword = createServerPassword(id = "1", username = "username")
        val login = createLogin(guid = "1", username = "username")

        assertEquals(Operation.UPDATE, getPersistenceOperation(login, serverPassword))
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
}

fun mockLoginsStorage(): AsyncLoginsStorage {
    val loginsStorage = mock<AsyncLoginsStorage>()
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

fun createLogin(guid: String?, username: String = "username") = Login(
    guid = guid,
    username = username,
    password = "password",
    origin = "origin"
)

fun createServerPassword(
    id: String = "id",
    password: String = "password",
    username: String = "username"
) = ServerPassword(
    id = id,
    hostname = "hostname",
    password = password,
    username = username,
    httpRealm = "httpRealm",
    formSubmitURL = "formsubmiturl"
)
