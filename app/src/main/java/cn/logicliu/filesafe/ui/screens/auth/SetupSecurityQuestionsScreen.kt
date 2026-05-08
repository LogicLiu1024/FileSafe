package cn.logicliu.filesafe.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.logicliu.filesafe.security.SecurityQuestion
import cn.logicliu.filesafe.security.PasswordManager
import cn.logicliu.filesafe.security.SecurityQuestionManager
import cn.logicliu.filesafe.security.SecuritySettingsManager
import cn.logicliu.filesafe.ui.viewmodel.AuthViewModel
import cn.logicliu.filesafe.ui.viewmodel.LoginState
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupSecurityQuestionsScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val passwordManager = remember { PasswordManager(context) }
    val securityQuestionManager = remember { SecurityQuestionManager(context) }
    val securitySettingsManager = remember { SecuritySettingsManager(context) }
    val viewModel = remember {
        AuthViewModel(passwordManager, securityQuestionManager, securitySettingsManager)
    }

    val loginState by viewModel.loginState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val defaultQuestions = listOf(
        "您最喜欢的动物是什么？",
        "您出生的城市是？",
        "您最喜欢的电影是？",
        "您小学的名称是？",
        "您最喜欢的食物是？",
        "您第一个宠物叫什么？",
        "您父亲的姓名是？",
        "您母亲的姓名是？"
    )

    val questions = remember { mutableStateListOf<SecurityQuestion>() }
    
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var selectedQuestion by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Error) {
            snackbarHostState.showSnackbar((loginState as LoginState.Error).message)
        }
    }

    fun isValidQuestion(question: String, currentAnswers: List<String>): Boolean {
        if (question.isBlank() || answer.isBlank()) return false
        val existingAnswers = questions.map { it.answer.lowercase() }
        return !existingAnswers.contains(answer.lowercase()) && 
               !questions.any { it.question == question }
    }

    fun moveToNextQuestion() {
        if (selectedQuestion.isNotBlank() && answer.isNotBlank()) {
            questions.add(SecurityQuestion(selectedQuestion, answer))
            
            if (questions.size >= 3) {
                viewModel.saveSecurityQuestions(questions.toList())
                onSetupComplete()
            } else {
                currentQuestionIndex++
                selectedQuestion = ""
                answer = ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置密保问题") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.QuestionAnswer,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "设置密保问题",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "请设置 ${questions.size + 1}/3 个密保问题",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "密保问题用于找回密码，请妥善保管",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (questions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "已设置的问题：",
                            style = MaterialTheme.typography.labelMedium
                        )
                        questions.forEachIndexed { index, q ->
                            Text(
                                text = "${index + 1}. ${q.question}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedQuestion,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("选择密保问题") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    val availableQuestions = defaultQuestions.filter { q ->
                        !questions.any { it.question == q }
                    }
                    
                    availableQuestions.forEach { question ->
                        DropdownMenuItem(
                            text = { Text(question) },
                            onClick = {
                                selectedQuestion = question
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                label = { Text("输入答案") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { moveToNextQuestion() },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedQuestion.isNotBlank() && answer.isNotBlank()
            ) {
                Text(if (questions.size >= 2) "完成设置" else "下一题")
            }

            if (questions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "已设置 ${questions.size}/3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
