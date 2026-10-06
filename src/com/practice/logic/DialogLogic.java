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
        UserSession session = sessions.computeIfAbsent(userId, id -> new UserSession());

        if (input != null && input.equalsIgnoreCase("/help")) {
            return questionProvider.getHelpMessage();
        }

        if (session.isWaitingForAnswer()) {
            int idx = session.getCurrentQuestionIndex();
            boolean correct = questionProvider.checkAnswer(idx, input);
            session.setWaitingForAnswer(false);
            session.incrementQuestionIndex();
            return correct ? "Верно!" : "Неверно.";
        }

        int idx = session.getCurrentQuestionIndex();
        if (idx >= questionProvider.getTotalQuestions()) {
            return "Вопросы закончились! Спасибо за игру.";
        }
        session.setWaitingForAnswer(true);
        return questionProvider.getQuestionByIndex(idx);
    }
}
