package com.navigator.app.ui.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navigator.app.app.AppContainer
import com.navigator.app.data.permissions.PermissionId
import com.navigator.app.data.permissions.PermissionsRepository
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.data.settings.SettingsRepository
import com.navigator.app.nav.providers.GoogleNavSdkController
import com.navigator.app.ui.feature.permissions.FixStep
import com.navigator.app.ui.feature.permissions.PermissionFix
import com.navigator.app.ui.feature.settings.PermissionsUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

data class OnboardingUi(
    val step: OnboardingStep,
    val engine: NavEngine,
    val name: String,
    /** (position, total) from the step after the welcome; null on the welcome. */
    val progress: Pair<Int, Int>?,
    val canGoBack: Boolean,
    val permissions: PermissionsUi,
)

sealed interface OnboardingEvent {
    /** Onboarding is done: on to pairing. */
    data object Finished : OnboardingEvent
}

/** The first-run steps. Choices are saved as they are made, so leaving halfway keeps them. */
class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val permissions: PermissionsRepository,
    private val engineAvailable: Boolean,
    private val termsAcceptedAtStart: Boolean,
) : ViewModel() {
    private val step = MutableStateFlow(OnboardingStep.WELCOME)
    private val engine = MutableStateFlow(if (engineAvailable) settings.snapshot.value.engine else NavEngine.MIRROR)
    private val name = MutableStateFlow(settings.snapshot.value.userName.orEmpty())
    private val termsDone = MutableStateFlow(termsAcceptedAtStart)
    private var finished = false

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingEvent> = _events.receiveAsFlow()

    private fun steps() = OnboardingFlow.steps(engineAvailable, engine.value, termsDone.value)

    private fun build(step: OnboardingStep, engine: NavEngine, name: String): OnboardingUi {
        val steps = OnboardingFlow.steps(engineAvailable, engine, termsDone.value)
        return OnboardingUi(
            step, engine, name, OnboardingFlow.progress(steps, step),
            canGoBack = OnboardingFlow.previous(steps, step) != null,
            permissions = PermissionsUi.of(permissions.state.value),
        )
    }

    val ui: StateFlow<OnboardingUi> = combine(step, engine, name, permissions.state.map(PermissionsUi::of), termsDone) { s, e, n, p, _ ->
        build(s, e, n).copy(permissions = p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(step.value, engine.value, name.value))

    fun refreshPermissions() = permissions.refresh()

    fun chooseEngine(choice: NavEngine) { if (engineAvailable) engine.value = choice }

    fun setName(text: String) { name.value = text.take(MAX_NAME) }

    fun next() {
        when (step.value) {
            OnboardingStep.ENGINE -> saveEngine()
            OnboardingStep.NAME -> settings.update { userName = name.value.trim() }
            OnboardingStep.PERMISSIONS -> { finish(); return }
            else -> {}
        }
        OnboardingFlow.next(steps(), step.value)?.let { step.value = it }
    }

    /** Skip on the name step: nothing is saved and the greeting stays "Welcome!". */
    fun skipName() {
        OnboardingFlow.next(steps(), OnboardingStep.NAME)?.let { step.value = it }
    }

    fun back() {
        OnboardingFlow.previous(steps(), step.value)?.let { step.value = it }
    }

    /** Google's dialog was accepted: the step is done and Back no longer returns to it. */
    fun termsAccepted() {
        next()
        termsDone.value = true
    }

    fun useMirror() {
        engine.value = NavEngine.MIRROR
        saveEngine()
        step.value = OnboardingFlow.next(steps(), OnboardingStep.TERMS) ?: OnboardingStep.NAME
    }

    /** What the primary button on the permissions step does: the next required permission still off, or finish. */
    fun nextPermissionStep(): FixStep? =
        PermissionFix.nextRequired(permissions.state.value)?.let { PermissionFix.stepFor(it, permissions.state.value) }

    fun stepFor(id: PermissionId): FixStep = PermissionFix.stepFor(id, permissions.state.value)

    fun finish() {
        if (finished) return
        finished = true
        settings.update { onboardingComplete = true }
        _events.trySend(OnboardingEvent.Finished)
    }

    private fun saveEngine() { if (engineAvailable) settings.update { googleNavEnabled = engine.value == NavEngine.IN_APP } }

    companion object {
        const val MAX_NAME = 30

        fun factory(container: AppContainer, context: android.content.Context) = object : ViewModelProvider.Factory {
            private val app = context.applicationContext

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val available = GoogleNavSdkController.isAvailable(app)
                return OnboardingViewModel(
                    container.settings, container.permissions, available, termsAcceptedAtStart = !available || GoogleTerms.accepted(app),
                ) as T
            }
        }
    }
}
