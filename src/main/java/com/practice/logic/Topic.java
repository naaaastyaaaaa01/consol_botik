package com.practice.logic;

public enum Topic {
    VECTOR_SPACES("Векторные пространства"),
    MATRICES("Матрицы"),
    LINEAR_MAPS("Линейные отображения и линейные операторы"),
    EUCLIDEAN("Евклидовы пространства"),
    QUADRATIC_FORMS("Квадратичные формы"),
    PLANE_QUADRICS("Квадрики на плоскости"),
    SPACE_QUADRICS("Квадрики в пространстве");

    private final String title;

    Topic(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** Тема по номеру вопроса в файле (нумерация с 1). */
    public static Topic byQuestionNumber(int number) {
        if (number <= 11) return VECTOR_SPACES;
        if (number <= 25) return MATRICES;
        if (number <= 37) return LINEAR_MAPS;
        if (number <= 53) return EUCLIDEAN;
        if (number <= 60) return QUADRATIC_FORMS;
        if (number <= 68) return PLANE_QUADRICS;
        return SPACE_QUADRICS;
    }
}
