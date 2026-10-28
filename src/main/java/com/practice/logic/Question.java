package com.practice.logic;

public class Question {

    private final String text;
    private final String[] options;
    private final int correctOption;
    private final Topic topic;
    private final Difficulty difficulty;
    private final String hint;
    private final String explanation;

    public Question(String text, String[] options, int correctOption) {
        this(text, options, correctOption, null, null, "", "");
    }

    public Question(String text, String[] options, int correctOption,
                    Topic topic, Difficulty difficulty,
                    String hint, String explanation) {
        this.text = text;
        this.options = options;
        this.correctOption = correctOption;
        this.topic = topic;
        this.difficulty = difficulty;
        this.hint = hint;
        this.explanation = explanation;
    }

    public String getText() {
        return text;
    }

    public String[] getOptions() {
        return options;
    }

    public int getCorrectOption() {
        return correctOption;
    }

    public Topic getTopic() {
        return topic;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getHint() {
        return hint;
    }

    public String getExplanation() {
        return explanation;
    }

    public boolean isCorrect(int answer) {
        return answer == correctOption;
    }
}
