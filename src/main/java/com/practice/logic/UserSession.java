package com.practice.logic;

public class UserSession {
    private int currentQuestionIndex = 0;
    private boolean waitingForAnswer = false;

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void incrementQuestionIndex() {
        currentQuestionIndex++;
    }

    public boolean isWaitingForAnswer() {
        return waitingForAnswer;
    }

    public void setWaitingForAnswer(boolean waitingForAnswer) {
        this.waitingForAnswer = waitingForAnswer;
    }
}
