package com.example.util

/**
 * Universal Master Key Override manager for DASMO Lock ecosystem.
 * Allows administrative recovery for the vault and app lock.
 */
object MasterKeyManager {
    const val MASTER_KEY = "DASMO SUBHOJIT"

    /**
     * Validates whether the given input matches the Master Key override.
     * Case-insensitive and trims / normalizes multiple whitespace characters.
     */
    fun isMasterKey(input: String): Boolean {
        if (input.isBlank()) return false
        val normalized = input.trim().replace("\\s+".toRegex(), " ")
        return normalized.equals(MASTER_KEY, ignoreCase = true)
    }
}
