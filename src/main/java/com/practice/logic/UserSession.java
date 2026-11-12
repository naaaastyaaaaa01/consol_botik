package com.practice.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UserSession {

    public enum Stage {
        START,              // ещё ничего не показано
        CHOOSE_TOPIC,       // ждём выбор темы
        CHOOSE_DIFFICULTY,  // ждём выбор сложности
        ANSWERING,          // ждём ответ на вопрос
        FINISHED            // вопросы закончились
    }

    private Stage stage = Stage.START;

    // выбранные параметры (null = любая тема / любая сложность)
    private Topic topic;
    private Difficulty difficulty;

    // очередь вопросов: индексы в QuestionProvider
    private List<Integer> queue = new ArrayList<>();
    // позиция в очереди
    private int currentQuestionIndex = 0;

    private boolean reviewMode = false;
    private int correctCount = 0;

    // индексы вопросов, на которые отвечали неправильно
    private final Set<Integer> wrongQuestions = new LinkedHashSet<>();

    public Stage getStage() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public void incrementQuestionIndex() {
        currentQuestionIndex++;
    }

    public boolean isWaitingForAnswer() {
        return stage == Stage.ANSWERING;
    }

    public List<Integer> getQueue() {
        return queue;
    }

    /** Начать новый проход по списку вопросов. */
    public void startRound(List<Integer> newQueue, boolean review) {
        this.queue = new ArrayList<>(newQueue);
        this.currentQuestionIndex = 0;
        this.correctCount = 0;
        this.reviewMode = review;
        this.stage = Stage.ANSWERING;
    }

    /** Индекс текущего вопроса в QuestionProvider или -1, если очередь закончилась. */
    public int getCurrentProviderIndex() {
        if (currentQuestionIndex < 0 || currentQuestionIndex >= queue.size()) {
            return -1;
        }
        return queue.get(currentQuestionIndex);
    }

    public boolean isQueueFinished() {
        return currentQuestionIndex >= queue.size();
    }

    public boolean isReviewMode() {
        return reviewMode;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void incrementCorrectCount() {
        correctCount++;
    }

    public Set<Integer> getWrongQuestions() {
        return wrongQuestions;
    }
}
