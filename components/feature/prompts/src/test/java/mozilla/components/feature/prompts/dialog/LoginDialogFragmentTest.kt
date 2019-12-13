package mozilla.components.feature.prompts.dialog

import mozilla.components.concept.engine.Hint
import mozilla.components.concept.engine.Login
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginDialogFragmentTest {

    private lateinit var fragment: LoginDialogFragment

    @Before
    fun before() {
        fragment = spy(LoginDialogFragment.testInstance(
            sessionId = "",
            hint = Hint(),
            login = Login()
        ))
    }

    @Test
    fun `fragment construction does not trigger updateSaveButton`() {
        Mockito.verify(fragment, times(0)).updateSaveButton()
    }

    @Test
    fun `updateSaveButton is called when username is updated`() {
        fragment.username = "new_username"
        Mockito.verify(fragment, times(1)).updateSaveButton()

        fragment.username = ""
        Mockito.verify(fragment, times(2)).updateSaveButton()

        fragment.username = ""
        Mockito.verify(fragment, times(3)).updateSaveButton()
    }
}
