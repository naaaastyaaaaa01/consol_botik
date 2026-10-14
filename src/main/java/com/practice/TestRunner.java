package com.practice;

import com.practice.data.InMemoryQuestionProvider;
import com.practice.logic.DialogLogic;
import com.practice.logic.Question;
import com.practice.logic.QuestionProvider;

public class TestRunner {

    public static void main(String[] args) {

        TestRunner test = new TestRunner();

        test.testQuestionCount();
        test.testGetQuestion();
        test.testQuestionOptions();
        test.testCorrectAnswer();
        test.testWrongAnswer();
        test.testInvalidAnswer();
        test.testHelp();
        test.testDifferentUsers();

        System.out.println();
        System.out.println("=================================");
        System.out.println("Все тесты пройдены успешно!");
        System.out.println("=================================");
    }

    // Проверяем количество вопросов
    public void testQuestionCount() {

        InMemoryQuestionProvider provider =
                new InMemoryQuestionProvider();

        if (provider.getTotalQuestions() != 80) {
            throw new AssertionError(
                    "Ошибка: должно быть 80 вопросов, а сейчас "
                            + provider.getTotalQuestions()
            );
        }

        System.out.println("testQuestionCount — OK");
    }

    // Проверяем получение первого вопроса
    public void testGetQuestion() {

        InMemoryQuestionProvider provider =
                new InMemoryQuestionProvider();

        Question question =
                provider.getQuestionByIndex(0);

        if (question == null) {
            throw new AssertionError(
                    "Ошибка: первый вопрос не найден"
            );
        }

        if (question.getText() == null ||
                question.getText().isEmpty()) {

            throw new AssertionError(
                    "Ошибка: текст вопроса пустой"
            );
        }

        System.out.println("testGetQuestion — OK");
    }

    // Проверяем, что у вопроса есть 4 варианта ответа
    public void testQuestionOptions() {

        InMemoryQuestionProvider provider =
                new InMemoryQuestionProvider();

        Question question =
                provider.getQuestionByIndex(0);

        String[] options =
                question.getOptions();

        if (options == null) {
            throw new AssertionError(
                    "Ошибка: варианты ответа отсутствуют"
            );
        }

        if (options.length != 4) {
            throw new AssertionError(
                    "Ошибка: у вопроса должно быть 4 варианта ответа"
            );
        }

        for (String option : options) {

            if (option == null || option.isEmpty()) {
                throw new AssertionError(
                        "Ошибка: один из вариантов ответа пустой"
                );
            }
        }

        System.out.println("testQuestionOptions — OK");
    }

    // Проверяем правильный ответ
    public void testCorrectAnswer() {

        InMemoryQuestionProvider provider =
                new InMemoryQuestionProvider();

        Question question =
                provider.getQuestionByIndex(0);

        int correctAnswer =
                question.getCorrectOption();

        if (!question.isCorrect(correctAnswer)) {
            throw new AssertionError(
                    "Ошибка: правильный ответ определяется неправильно"
            );
        }

        System.out.println("testCorrectAnswer — OK");
    }

    // Проверяем неправильный ответ
    public void testWrongAnswer() {

        InMemoryQuestionProvider provider =
                new InMemoryQuestionProvider();

        Question question =
                provider.getQuestionByIndex(0);

        int correctAnswer =
                question.getCorrectOption();

        int wrongAnswer;

        if (correctAnswer == 1) {
            wrongAnswer = 2;
        } else {
            wrongAnswer = 1;
        }

        if (question.isCorrect(wrongAnswer)) {
            throw new AssertionError(
                    "Ошибка: неправильный ответ определяется как правильный"
            );
        }

        System.out.println("testWrongAnswer — OK");
    }

    // Проверяем неправильный номер ответа
    public void testInvalidAnswer() {

        InMemoryQuestionProvider provider =
                new InMemoryQuestionProvider();

        Question question =
                provider.getQuestionByIndex(0);

        if (question.isCorrect(0)) {
            throw new AssertionError(
                    "Ошибка: ответ 0 не должен быть правильным"
            );
        }

        if (question.isCorrect(5)) {
            throw new AssertionError(
                    "Ошибка: ответ 5 не должен быть правильным"
            );
        }

        System.out.println("testInvalidAnswer — OK");
    }

    // Проверяем команду /help
    public void testHelp() {

        QuestionProvider provider =
                new InMemoryQuestionProvider();

        DialogLogic logic =
                new DialogLogic(provider);

        String result =
                logic.processInput("user1", "/help");

        if (result == null) {
            throw new AssertionError(
                    "Ошибка: /help ничего не вернул"
            );
        }

        if (!result.contains("/help")) {
            throw new AssertionError(
                    "Ошибка: в справке нет команды /help"
            );
        }

        System.out.println("testHelp — OK");
    }

    // Проверяем, что разные пользователи имеют отдельные сессии
    public void testDifferentUsers() {

        QuestionProvider provider =
                new InMemoryQuestionProvider();

        DialogLogic logic =
                new DialogLogic(provider);

        String firstUser =
                logic.processInput("user1", "");

        String secondUser =
                logic.processInput("user2", "");

        if (firstUser == null || secondUser == null) {
            throw new AssertionError(
                    "Ошибка: вопрос не был получен"
            );
        }

        if (!firstUser.equals(secondUser)) {
            throw new AssertionError(
                    "Ошибка: разные пользователи получили разные первые вопросы"
            );
        }

        System.out.println("testDifferentUsers — OK");
    }
}