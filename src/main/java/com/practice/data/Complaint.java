
package com.practice.data;

public class Complaint {

    private final int id;
    private final String telegramId;
    private final int questionIndex;
    private final String questionText;
    private final String comment;
    private final long createdAt;

    public Complaint(
            int id,
            String telegramId,
            int questionIndex,
            String questionText,
            String comment,
            long createdAt
    ) {
        this.id = id;
        this.telegramId = telegramId;
        this.questionIndex = questionIndex;
        this.questionText = questionText;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getTelegramId() {
        return telegramId;
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getComment() {
        return comment;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
