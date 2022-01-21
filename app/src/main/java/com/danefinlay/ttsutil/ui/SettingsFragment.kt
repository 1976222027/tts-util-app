/*
 * TTS Util
 *
 * Authors: Dane Finlay <Danesprite@posteo.net>
 *
 * Copyright (C) 2019 Dane Finlay
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.danefinlay.ttsutil.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.Voice
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.view.ContextThemeWrapper
import com.danefinlay.ttsutil.ApplicationEx
import com.danefinlay.ttsutil.R
import com.danefinlay.ttsutil.Speaker
import com.danefinlay.ttsutil.isReady
import org.jetbrains.anko.toast

/**
 * A [Fragment] subclass for app and TTS settings.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private val myActivity: SpeakerActivity
        get() = (activity as SpeakerActivity)

    private val myApplication: ApplicationEx
        get() = myActivity.myApplication

    private val speaker: Speaker?
        get() = myActivity.speaker

    override fun onCreatePreferences(savedInstanceState: Bundle?,
                                     rootKey: String?) {
        // Load the preferences from an XML resource.
        setPreferencesFromResource(R.xml.prefs, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        // Handle TTS engine preferences.
        if (handleTtsEnginePrefs(preference)) return true

        return super.onPreferenceTreeClick(preference)
    }

    private fun handleTtsEnginePrefs(preference: Preference?): Boolean {
        val key = preference?.key

        // Handle opening the system settings.
        if (key == "pref_tts_system_settings") {
            myActivity.openSystemTTSSettings()
            return true
        }

        val speaker = speaker
        if (speaker == null || !speaker.isReady()) {
            // Show the speaker not ready message.
            myActivity.showSpeakerNotReadyMessage()
            return true
        }

        // Handle preferences using the Speaker.
        return when (key) {
            "pref_tts_engine" -> handleSetTtsEngine(key, speaker)
            "pref_tts_voice" -> handleSetTtsVoice(key, speaker)
            "pref_tts_pitch" -> handleSetTtsPitch(key, speaker)
            "pref_tts_speech_rate" -> handleSetTtsSpeechRate(key, speaker)
            else -> false  // not a TTS engine preference.
        }
    }

    private fun displayAlertDialog(title: Int, items: List<String>,
                                   checkedItem: Int,
                                   onClickPositiveListener: (index: Int) -> Unit,
                                   onClickNeutralListener: (() -> Unit)?) {
        val context = ContextThemeWrapper(context, R.style.AlertDialogTheme)
        AlertDialog.Builder(context).apply {
            setTitle(title)
            var selection = checkedItem
            setSingleChoiceItems(items.toTypedArray(), checkedItem) { _, index ->
                selection = index
            }
            setPositiveButton(R.string.alert_positive_message) { _, _ ->
                if (selection >= 0 && selection < items.size) {
                    onClickPositiveListener(selection)
                }
            }
            if (onClickNeutralListener != null) {
                setNeutralButton(R.string.use_default_tts_preference) { _, _ ->
                    onClickNeutralListener()
                }
            }
            show()
        }
    }

    private fun handleSetTtsEngine(preferenceKey: String,
                                   speaker: Speaker): Boolean {
        // Get a list of the available TTS engines.
        val tts = speaker.tts
        val engines = tts.engines?.toList()?.sortedBy { it.label } ?: return true
        val engineNames = engines.map { it.label }
        val enginePackages = engines.map { it.name }

        // Get the previous or default voice.
        val prefs = preferenceManager.sharedPreferences
        val currentValue = prefs.getString(preferenceKey, tts.defaultEngine)
        val currentIndex = enginePackages.indexOf(currentValue)

        // Show a list alert dialog of the available TTS engines.
        val dialogTitle = R.string.pref_tts_engine_summary
        val onClickPositiveListener = { index: Int ->
            // Get the package name from the index of the selected item and
            // use it to set the current engine.
            val packageName = engines.map { it.name }[index]
            myApplication.reinitialiseSpeaker(myActivity, packageName)

            // Set the engine's name in the preferences.
            prefs.edit().putString(preferenceKey, packageName).apply()
        }
        val onClickNeutralListener = {
            // Remove the preferred engine's name from the preferences.
            prefs.edit().putString(preferenceKey, null).apply()

            // Set the default engine.
            myApplication.reinitialiseSpeaker(myActivity, null)
        }
        displayAlertDialog(dialogTitle, engineNames, currentIndex,
                onClickPositiveListener, onClickNeutralListener)
        return true
    }

    private fun chooseVoiceSubSelection(prefs: SharedPreferences,
                                        speaker: Speaker,
                                        preferenceKey: String,
                                        currentVoice: Voice?,
                                        voiceSelection: List<Voice>) {
        // Get a list of display names, adding a number after each non-distinct
        // name.
        // TODO Add "(network required)" before the number for voices that require
        //  network connectivity (isNetworkConnectionRequired).
        //  This should also be done for the first menu and the string should be
        //  placed in strings.xml.
        val displayName = voiceSelection[0].locale.displayName
        val displayNames = voiceSelection
                .mapIndexed { i, _ -> "$displayName ${i + 1}" }

        // Define the positive button listener.
        val onClickPositiveListener = { index: Int ->
            // Set the selected voice.
            val selectedVoice = voiceSelection[index]
            speaker.voice = selectedVoice

            // Set the voice's name in the preferences.
            prefs.edit().putString(preferenceKey, selectedVoice.name)
                    .apply()
        }

        // Set the current index as either the current voice, if present, or the
        // first voice on the list.
        val currentVoiceIndex = voiceSelection.indexOf(currentVoice)
        val currentIndex = if (currentVoiceIndex > 0) currentVoiceIndex else 0

        // Display the dialog.
        val dialogTitle = R.string.pref_tts_voice_summary
        displayAlertDialog(dialogTitle, displayNames, currentIndex,
                onClickPositiveListener, null)
    }

    private fun handleSetTtsVoice(preferenceKey: String,
                                  speaker: Speaker): Boolean {
        // Get the set of available TTS voices.
        // Return early if the engine returned no voices.
        val voices = speaker.voices
        if (voices.isEmpty()) {
            context?.toast(R.string.no_tts_voices_msg)
            return true
        }

        // Get a list of voices.
        val voicesList = voices.toList().filterNotNull()

        // Retrieve the previous voice, falling back on the default.
        val defaultVoice = speaker.defaultVoice
        val prefs = preferenceManager.sharedPreferences
        val currentVoiceName = prefs.getString(preferenceKey,
                speaker.voice?.name ?: defaultVoice?.name
        )
        val currentVoice = voicesList.find { it.name == currentVoiceName }

        // Show the user a list of voice display names sorted by language.
        val voicesByDisplayName = voicesList.groupBy { it.locale.displayName }
        val displayNames = voicesByDisplayName.map { it.value[0].locale }
                .sortedBy { it.displayLanguage }.map { it.displayName }
        val currentIndex = displayNames.indexOf(currentVoice?.locale?.displayName)
        val dialogTitle = R.string.pref_tts_voice_summary
        val onClickPositiveListener: (Int) -> Unit = { index: Int ->
            // Retrieve the list of voices for the selected display name and handle
            // selecting one.
            val item = displayNames[index]
            val voiceSelection = voicesByDisplayName[item]!!

            // It is noted here that, at least if Google's TTS engine is used, this
            // may provide greater user choice than the system settings.

            if (voiceSelection.size > 1) {
                // Display another dialog to the user for selection.
                chooseVoiceSubSelection(prefs, speaker, preferenceKey, currentVoice,
                        voiceSelection)
            } else {
                // There is no sense in showing another dialog for only one option.
                // Set the first and only voice in the selection.
                val selectedVoice = voiceSelection[0]
                speaker.voice = selectedVoice

                // Set the voice's name in the preferences.
                prefs.edit().putString(preferenceKey, selectedVoice.name)
                        .apply()
            }
        }
        val onClickNeutralListener = {
            // Use the default TTS voice/language.
            if (defaultVoice != null) {
                speaker.voice = defaultVoice
            } else {
                speaker.tts.language = myApplication.currentSystemLocale
            }

            // Remove the current voice's name from the preferences.
            prefs.edit().putString(preferenceKey, null).apply()
        }
        displayAlertDialog(dialogTitle, displayNames, currentIndex,
                onClickPositiveListener, onClickNeutralListener)
        return true
    }

    private fun handleSetTtsPitch(preferenceKey: String,
                                  speaker: Speaker): Boolean {
        // Define a list of pitch values and their string representations.
        val pitches = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val pitchStrings = pitches.map { it.toString() }

        // Get the previous or default pitch value.
        val prefs = preferenceManager.sharedPreferences
        val currentValue = prefs.getFloat(preferenceKey, 1.0f)
        val currentIndex = pitches.indexOf(currentValue)

        // Show a list alert dialog of pitch choices.
        val dialogTitle = R.string.pref_tts_pitch_summary
        val onClickPositiveListener = { index: Int ->
            // Get the pitch from the index of the selected item and
            // use it to set the current voice pitch.
            val pitch = pitches[index]
            speaker.tts.setPitch(pitch)

            // Set the pitch in the preferences.
            prefs.edit().putFloat(preferenceKey, pitch).apply()
        }
        val onClickNeutralListener = {
            // Remove the preferred pitch from the preferences.
            prefs.edit().remove(preferenceKey).apply()

            // Reinitialise the TTS engine so it uses the pitch as set in the system
            // TTS settings.
            myApplication.reinitialiseSpeaker(myActivity, null)
        }
        displayAlertDialog(dialogTitle, pitchStrings, currentIndex,
                onClickPositiveListener, onClickNeutralListener)
        return true
    }

    private fun handleSetTtsSpeechRate(preferenceKey: String,
                                       speaker: Speaker): Boolean {
        // Define a list of speech rate values and their string representations.
        val speechRates = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f,
                4.0f, 5.0f)
        val speechRateStrings = speechRates.map { it.toString() }

        // Get the previous or default speech rate value.
        val prefs = preferenceManager.sharedPreferences
        val currentValue = prefs.getFloat(preferenceKey, 1.0f)
        val currentIndex = speechRates.indexOf(currentValue)

        // Show a list alert dialog of speech rate choices.
        val dialogTitle = R.string.pref_tts_speech_rate_summary
        val onClickPositiveListener = { index: Int ->
            // Get the speech rate from the index of the selected item and
            // use it to set the current speech rate.
            val speechRate = speechRates[index]
            speaker.tts.setSpeechRate(speechRate)

            // Set the speech rate in the preferences.
            prefs.edit().putFloat(preferenceKey, speechRate).apply()
        }
        val onClickNeutralListener = {
            // Remove the preferred speech rate from the preferences.
            prefs.edit().remove(preferenceKey).apply()

            // Reinitialise the TTS engine so it uses the speech rate as set in the
            // system TTS settings.
            myApplication.reinitialiseSpeaker(myActivity, null)
        }
        displayAlertDialog(dialogTitle, speechRateStrings, currentIndex,
                onClickPositiveListener, onClickNeutralListener)
        return true
    }
}
