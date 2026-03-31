package com.example.caremap.domain.service

interface EmergencyContactStore {
    fun getFamilyPhoneNumber(): String?

    fun saveFamilyPhoneNumber(phoneNumber: String)
}
