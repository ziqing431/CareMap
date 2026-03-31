package com.example.caremap.data.emergency

import android.content.Context
import com.example.caremap.domain.service.EmergencyContactStore

class SharedPreferencesEmergencyContactStore(
    context: Context,
) : EmergencyContactStore {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun getFamilyPhoneNumber(): String? {
        return preferences.getString(KEY_FAMILY_PHONE_NUMBER, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    override fun saveFamilyPhoneNumber(phoneNumber: String) {
        preferences.edit()
            .putString(KEY_FAMILY_PHONE_NUMBER, phoneNumber.trim())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "caremap_emergency_contacts"
        const val KEY_FAMILY_PHONE_NUMBER = "family_phone_number"
    }
}
