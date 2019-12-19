/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.prompts.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.Hint
import mozilla.components.concept.engine.Login
import mozilla.components.feature.prompts.R
import mozilla.components.feature.prompts.logins.LoginValidationDelegate
import mozilla.components.support.ktx.android.content.appName
import mozilla.components.support.ktx.android.view.toScope
import java.lang.RuntimeException
import kotlin.reflect.KProperty
import com.google.android.material.R as MaterialR

private const val KEY_LOGIN_HINT = "KEY_LOGIN_HINT"
private const val KEY_LOGIN = "KEY_LOGIN"

/**
 * [android.support.v4.app.DialogFragment] implementation to display a
 * dialog that allows users to save/update usernames and passwords for a given domain.
 */
internal class LoginDialogFragment : PromptDialogFragment() {

    var stateUpdate: Job? = null

    private inner class SafeArgParcelable<T : Parcelable>(private val key: String) {
        operator fun getValue(frag: LoginDialogFragment, prop: KProperty<*>): T =
            safeArguments.getParcelable<T>(key)!!

        operator fun setValue(frag: LoginDialogFragment, prop: KProperty<*>, value: T?) {
            safeArguments.putParcelable(key, value)
        }
    }

    internal var hint by SafeArgParcelable<Hint>(KEY_LOGIN_HINT)
    internal var login by SafeArgParcelable<Login>(KEY_LOGIN)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), this.theme).apply {
            setOnShowListener {
                val bottomSheet =
                    findViewById<View>(MaterialR.id.design_bottom_sheet) as FrameLayout
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflateRootView(container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val hostView = view.findViewById<TextView>(R.id.host_name)
        hostView.text = login.origin

        val saveMessage = view.findViewById<TextView>(R.id.save_message)

        saveMessage.text =
            getString(R.string.mozac_feature_prompt_logins_save_message, activity?.appName)

        val saveConfirm = view.findViewById<Button>(R.id.save_confirm)
        val cancelButton = view.findViewById<Button>(R.id.save_cancel)

        saveConfirm.setOnClickListener {
            onPositiveClickAction()
        }

        cancelButton.setOnClickListener {
            feature?.onCancel(sessionId)
        }
        update(login)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        feature?.onCancel(sessionId)
    }

    private fun onPositiveClickAction() {
        feature?.onConfirm(sessionId, login)
    }

    private fun inflateRootView(container: ViewGroup? = null): View {
        val rootView = LayoutInflater.from(requireContext()).inflate(
            R.layout.mozac_feature_prompt_login_prompt,
            container,
            false
        )
        bindUsername(rootView)
        bindPassword(rootView)
        return rootView
    }

    private fun bindUsername(view: View) {
        val usernameEditText = view.findViewById<TextInputEditText>(R.id.username_field)

        usernameEditText.setText(login.username)
        usernameEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                login.username = editable.toString()
                update(login)
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                Unit
        })
    }

    private fun bindPassword(view: View) {
        val passwordEditText = view.findViewById<TextInputEditText>(R.id.password_field)

        passwordEditText.setText(login.password)
        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                login.password = editable.toString()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun update(login: Login) {
        val scope = view?.toScope() ?: return
        stateUpdate?.cancel()
        stateUpdate = scope.launch {
            val result =
                feature?.loginValidationDelegate?.validateCanPersist(login)?.await()

            when(result) {
                is LoginValidationDelegate.Result.CanBeCreated ->
                    setViewState(confirmText = R.string.mozac_feature_prompt_save_confirmation)
                is LoginValidationDelegate.Result.CanBeUpdated ->
                    setViewState(confirmText = R.string.mozac_feature_prompt_update_confirmation)
                is LoginValidationDelegate.Result.Error.EmptyPassword ->
                    setViewState(confirmText = R.string.mozac_feature_prompt_save_confirmation,
                        passwordErrorText = R.string.mozac_feature_prompt_error_empty_password)
                is LoginValidationDelegate.Result.Error.NotImplemented ->
                    throw NotImplementedError()
                is LoginValidationDelegate.Result.Error.GeckoError ->
                    throw RuntimeException("Unexpected problem while accessing storage. Cause: ${result.exception}")
            }
        }
    }

    private fun setViewState(
        @StringRes confirmText: Int,
        confirmButtonEnabled: Boolean = true,
        @StringRes passwordErrorText: Int? = null
    ) {
        view?.findViewById<Button>(R.id.save_confirm)?.text = context?.getString(confirmText)
        view?.findViewById<Button>(R.id.save_confirm)?.isEnabled = confirmButtonEnabled
        view?.findViewById<TextInputLayout>(R.id.password_text_input_layout)?.error =
            passwordErrorText?.let { context?.getString(it) }
    }

    companion object {
        /**
         * A builder method for creating a [LoginDialogFragment]
         * @param sessionId the id of the session for which this dialog will be created.
         * @param hint a value that helps to determine the appropriate prompting behavior.
         * @param login represents login information on a given domain.
         * */
        fun newInstance(
            sessionId: String,
            hint: Hint,
            login: Login
        ): LoginDialogFragment {

            val fragment = LoginDialogFragment()
            val arguments = fragment.arguments ?: Bundle()

            with(arguments) {
                putString(KEY_SESSION_ID, sessionId)
                putParcelable(KEY_LOGIN_HINT, hint)
                putParcelable(KEY_LOGIN, login)
            }

            fragment.arguments = arguments
            return fragment
        }
    }
}
