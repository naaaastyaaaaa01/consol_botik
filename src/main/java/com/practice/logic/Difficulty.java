package com.practice.logic;

public enum Difficulty {
    EASY("Лёгкий"),
    MEDIUM("Средний"),
    HARD("Сложный");

    private final String title;

    Difficulty(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** Уровень по числу из файла: 1, 2 или 3. */
    public static Difficulty fromNumber(int number) {
        if (number < 1 || number > 3) {
            throw new IllegalArgumentException(
                    "Сложность должна быть от 1 до 3, получено: " + number
            );
        }
        return values()[number - 1];
    }
}
