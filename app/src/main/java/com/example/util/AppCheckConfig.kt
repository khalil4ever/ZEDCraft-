package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import java.util.UUID

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        if (BuildConfig.DEBUG) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )

            val intentToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
            val activeDebugSecret: String

            if (!intentToken.isNullOrEmpty()) {
                // CRITICAL: synchronous .commit() before DebugAppCheckProviderFactory reads it
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, intentToken)
                    .commit()
                activeDebugSecret = intentToken
                Log.d(TAG, "App Check debug secret configured from launch Intent extra.")
            } else {
                val existingSecret = prefs.getString(DEBUG_SECRET_KEY, null)?.trim()
                if (!existingSecret.isNullOrEmpty()) {
                    activeDebugSecret = existingSecret
                } else {
                    val generatedSecret = UUID.randomUUID().toString()
                    prefs.edit()
                        .putString(DEBUG_SECRET_KEY, generatedSecret)
                        .commit()
                    activeDebugSecret = generatedSecret
                }
            }

            // Print the debug token prominently to Logcat so it can be registered in the Firebase Console
            Log.i(TAG, "=========================================================================")
            Log.i(TAG, "ZEDCraft Firebase App Check Debug Token (register in Firebase Console):")
            Log.i(TAG, activeDebugSecret)
            Log.i(TAG, "=========================================================================")

            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            // Release build: Use Play Integrity provider primary, with reCAPTCHA Enterprise fallback
            try {
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "PlayIntegrityAppCheckProviderFactory installed for release build.")
            } catch (playIntegrityError: Exception) {
                Log.w(TAG, "PlayIntegrityAppCheckProviderFactory fallback to reCAPTCHA", playIntegrityError)
                val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
                if (resId != 0) {
                    val siteKey = context.getString(resId)
                    if (siteKey.isNotBlank()) {
                        firebaseAppCheck.installAppCheckProviderFactory(
                            RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                        )
                        Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
