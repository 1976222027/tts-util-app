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

import android.os.Build
import android.os.Bundle
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
                                   onClickPositiveListener: (index: Int) -> Unit) {
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
        displayAlertDialog(dialogTitle, engineNames, currentIndex) { index ->
            // Get the package name from the index of the selected item and
            // use it to set the current engine.
            val packageName = engines.map { it.name }[index]
            myApplication.reinitialiseSpeaker(myActivity, packageName)

            // Set the engine's name in the preferences.
            preferenceManager.sharedPreferences.edit()
                    .putString(preferenceKey, packageName)
                    .apply()
        }
        return true
    }

    private fun handleSetTtsVoice(preferenceKey: String,
                                  speaker: Speaker): Boolean {
        // Return early for SDK version 20 as only versions 21 and above support
        // setting the voice.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            context?.toast(R.string.cannot_set_tts_voice_sdk_20_msg)
            return true
        }

        // Get the set of available TTS voices.
        // Return early if the engine returned no voices.
        val tts = speaker.tts
        val voices = tts.voices
        if (voices == null || voices.isEmpty()) {
            context?.toast(R.string.no_tts_voices_msg)
            return true
        }

        // Get a list of voices, voice names and display names.
        // TODO: This doesn't work for multiple voices with the same display name.
        // This is done because I'm not sure how to implement that nicely.
        val voicesList = voices.toList().sortedBy { it.name }
                .distinctBy { it.locale.displayName }
        val voiceNames = voicesList.map { it.name }
        val voiceDisplayNames = voicesList.map { it.locale.displayName }

        // Get the previous or default voice.
        val prefs = preferenceManager.sharedPreferences
        val currentValue = prefs.getString(preferenceKey,
                tts.voice?.name ?: tts.defaultVoice?.name
        )
        val currentIndex = voiceNames.indexOf(currentValue)

        // Show a list alert dialog of the available TTS voices.
        val context = ContextThemeWrapper(context, R.style.AlertDialogTheme)
        val items = voiceDisplayNames.toTypedArray()
        AlertDialog.Builder(context).apply {
            setTitle(R.string.pref_tts_voice_summary)
            var selection = currentIndex
            setSingleChoiceItems(items, currentIndex) { _, index ->
                selection = index
            }
            setPositiveButton(R.string.alert_positive_message) { _, _ ->
                if (selection >= 0 && selection < items.size) {
                    // Get the Voice from the index of the selected item and
                    // set it to the current voice of the engine.
                    tts.voice = voicesList[selection]

                    // Set the voice's name in the preferences.
                    prefs.edit().putString(preferenceKey, voiceNames[selection])
                            .apply()
                }
            }
            setNeutralButton(R.string.use_default_tts_voice) { _, _ ->
                // Use the default TTS voice/language.
                val defaultVoice = tts.defaultVoice
                if (defaultVoice != null) {
                    tts.voice = defaultVoice
                } else {
                    tts.language = myApplication.currentSystemLocale
                }

                // Remove the current voice's name from the preferences.
                prefs.edit().putString(preferenceKey, null).apply()
            }
            show()
        }
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
        displayAlertDialog(dialogTitle, pitchStrings, currentIndex) { index ->
            // Get the pitch from the index of the selected item and
            // use it to set the current voice pitch.
            val pitch = pitches[index]
            speaker.tts.setPitch(pitch)

            // Set the pitch in the preferences.
            prefs.edit().putFloat(preferenceKey, pitch).apply()
        }
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
        displayAlertDialog(dialogTitle, speechRateStrings, currentIndex) { index ->
            // Get the speech rate from the index of the selected item and
            // use it to set the current speech rate.
            val speechRate = speechRates[index]
            speaker.tts.setSpeechRate(speechRate)

            // Set the speech rate in the preferences.
            prefs.edit().putFloat(preferenceKey, speechRate).apply()
        }
        return true
    }
}
