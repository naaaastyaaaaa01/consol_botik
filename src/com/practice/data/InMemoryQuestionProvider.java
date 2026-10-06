package com.practice.data;

import com.practice.logic.QuestionProvider;

import java.util.ArrayList;
import java.util.List;

public class InMemoryQuestionProvider implements QuestionProvider {

    private record QA(String question, String answer) {}

    private final List<QA> questions = new ArrayList<>();

    public InMemoryQuestionProvider() {
        // ===== ЗДЕСЬ ПОЗЖЕ ДОБАВИШЬ ВОПРОСЫ =====
        // Пример:
        // questions.add(new QA("Сколько будет 2+2?", "4"));
        // questions.add(new QA("Столица Франции?", "Париж"));
        // questions.add(new QA("Какой язык мы учим?", "Java"));
    }

    @Override
    public int getTotalQuestions() {
        return questions.size();
    }

    @Override
    public String getQuestionByIndex(int index) {
        if (index < 0 || index >= questions.size()) {
            return null;
        }
        return questions.get(index).question();
    }

    @Override
    public boolean checkAnswer(int index, String userAnswer) {
        if (index < 0 || index >= questions.size() || userAnswer == null) {
            return false;
        }
        return questions.get(index).answer().equalsIgnoreCase(userAnswer.trim());
    }

    @Override
    public String getHelpMessage() {
        return "Я бот-викторина. Задаю вопросы, ты отвечаешь. Введи /help для справки.";
    }
}
