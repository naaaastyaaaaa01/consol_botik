package com.practice.logic;

import java.util.HashMap;
import java.util.Map;

public class DialogLogic {

    private final QuestionProvider questionProvider;
    private final Map<String, UserSession> sessions = new HashMap<>();

    public DialogLogic(QuestionProvider questionProvider) {
        this.questionProvider = questionProvider;
    }

    public String processInput(String userId, String input) {

        UserSession session = sessions.computeIfAbsent(
                userId,
                id -> new UserSession()
        );

        if (input != null && input.equalsIgnoreCase("/help")) {
            return getHelpMessage();
        }

        if (session.isWaitingForAnswer()) {

            int answer;

            try {
                answer = Integer.parseInt(input.trim());
            } catch (Exception e) {
                return "Пожалуйста, введи номер ответа: 1, 2, 3 или 4.";
            }

            if (answer < 1 || answer > 4) {
                return "Пожалуйста, введи номер ответа от 1 до 4.";
            }

            int questionIndex = session.getCurrentQuestionIndex();

            Question question =
                    questionProvider.getQuestionByIndex(questionIndex);

            boolean correct = question.isCorrect(answer);

            session.setWaitingForAnswer(false);
            session.incrementQuestionIndex();

            String result;

            if (correct) {
                result = "✓ Верно!";
            } else {
                result = "✗ Неверно.\n"
                        + "Правильный ответ: "
                        + question.getCorrectOption();
            }

            if (session.getCurrentQuestionIndex()
                    >= questionProvider.getTotalQuestions()) {

                return result
                        + "\n\n🎉 Викторина завершена!";
            }

            session.setWaitingForAnswer(true);

            Question nextQuestion =
                    questionProvider.getQuestionByIndex(
                            session.getCurrentQuestionIndex()
                    );

            return result
                    + "\n\n"
                    + formatQuestion(
                    nextQuestion,
                    session.getCurrentQuestionIndex()
            );
        }

        Question question =
                questionProvider.getQuestionByIndex(
                        session.getCurrentQuestionIndex()
                );

        if (question == null) {
            return "Вопросы закончились!";
        }

        session.setWaitingForAnswer(true);

        return formatQuestion(
                question,
                session.getCurrentQuestionIndex()
        );
    }

    private String formatQuestion(
            Question question,
            int questionIndex) {

        StringBuilder result = new StringBuilder();

        result.append("┌──────────────────────────────┐\n");
        result.append("│      КАРТОЧКА ")
                .append(questionIndex + 1)
                .append(" / ")
                .append(questionProvider.getTotalQuestions())
                .append("       │\n");
        result.append("└──────────────────────────────┘\n\n");

        result.append(question.getText()).append("\n\n");

        String[] options = question.getOptions();

        for (int i = 0; i < options.length; i++) {
            result.append(i + 1)
                    .append(". ")
                    .append(options[i])
                    .append("\n");
        }

        result.append("\nВаш ответ: ");

        return result.toString();
    }

    public boolean isFinished(String userId) {

        UserSession session = sessions.get(userId);

        if (session == null) {
            return false;
        }

        return session.getCurrentQuestionIndex()
                >= questionProvider.getTotalQuestions();
    }

    private String getHelpMessage() {

        return "Я бот-викторина по линейной алгебре.\n\n"
                + "Я показываю карточки с вопросами.\n"
                + "В каждой карточке 4 варианта ответа.\n"
                + "Тебе нужно ввести номер правильного ответа: 1, 2, 3 или 4.\n\n"
                + "Команды:\n"
                + "/help — показать эту справку\n"
                + "exit — завершить игру.";
    }
}