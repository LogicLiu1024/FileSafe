package cn.logicliu.filesafe.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security_questions")

class SecurityQuestionManager(
    private val context: Context
) {
    private val dataStore = context.securityDataStore

    val securityQuestionsSet: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SECURITY_QUESTIONS_KEY] != null
    }

    suspend fun saveSecurityQuestions(questions: List<SecurityQuestion>) {
        val serialized = questions.joinToString(QUESTION_SEPARATOR) { "${it.question}$ANSWER_SEPARATOR${it.answer}" }
        dataStore.edit { preferences ->
            preferences[SECURITY_QUESTIONS_KEY] = serialized
        }
    }

    suspend fun getSecurityQuestions(): List<SecurityQuestion> {
        val serialized = dataStore.data.first()[SECURITY_QUESTIONS_KEY] ?: return emptyList()
        return try {
            serialized.split(QUESTION_SEPARATOR).filter { it.isNotBlank() }.mapNotNull { item ->
                val parts = item.split(ANSWER_SEPARATOR)
                if (parts.size == 2) {
                    SecurityQuestion(parts[0], parts[1])
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun verifyAnswer(questionIndex: Int, answer: String): Boolean {
        val questions = getSecurityQuestions()
        if (questionIndex >= questions.size) return false
        val question = questions[questionIndex]
        return question.answer.lowercase().trim() == answer.lowercase().trim()
    }

    suspend fun clearSecurityQuestions() {
        dataStore.edit { preferences ->
            preferences.remove(SECURITY_QUESTIONS_KEY)
        }
    }

    companion object {
        private val SECURITY_QUESTIONS_KEY = stringPreferencesKey("security_questions")
        private const val QUESTION_SEPARATOR = "|||"
        private const val ANSWER_SEPARATOR = ":::"
    }
}

data class SecurityQuestion(
    val question: String,
    val answer: String
)
