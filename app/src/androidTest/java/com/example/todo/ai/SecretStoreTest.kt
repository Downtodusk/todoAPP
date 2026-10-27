package com.example.todo.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.todo.data.settings.SecretStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretStoreTest {
    @Test fun encryptedEnvelopeUsesFreshNonceAndSurvivesNewStoreInstance() {
        val store = SecretStore()
        val binding = "OPENAI_COMPAT|https://fixture.example/v1"
        val first = store.seal("synthetic-secret", binding)
        val second = store.seal("synthetic-secret", binding)
        assertNotEquals(first, second)
        assertFalse(first.contains("synthetic-secret"))
        assertEquals("synthetic-secret", SecretStore().open(first, binding))
        assertThrows(Exception::class.java) { store.open(first, "ANTHROPIC|https://other.example") }
        val parts = first.split(':').toMutableList()
        parts[2] = (if (parts[2][0] == 'A') "B" else "A") + parts[2].substring(1)
        assertThrows(Exception::class.java) { store.open(parts.joinToString(":"), binding) }
    }
}
