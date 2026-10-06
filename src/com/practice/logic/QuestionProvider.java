package com.practice.logic;

public interface QuestionProvider {
    int getTotalQuestions();
    String getQuestionByIndex(int index);
    boolean checkAnswer(int index, String userAnswer);
    String getHelpMessage();
}
