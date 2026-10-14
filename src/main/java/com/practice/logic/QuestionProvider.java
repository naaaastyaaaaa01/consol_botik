package com.practice.logic;

public interface QuestionProvider {

    int getTotalQuestions();

    Question getQuestionByIndex(int index);
}