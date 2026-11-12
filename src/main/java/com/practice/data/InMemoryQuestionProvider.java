
package com.practice.data;

import com.practice.logic.Difficulty;
import com.practice.logic.Question;
import com.practice.logic.QuestionProvider;
import com.practice.logic.Topic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class InMemoryQuestionProvider implements QuestionProvider {

    private static final int LINES_PER_QUESTION = 9;

    private final List<Question> questions = new ArrayList<>();

    public InMemoryQuestionProvider() {

        InputStream inputStream =
                getClass().getResourceAsStream("/questions.txt");

        if (inputStream == null) {
            throw new RuntimeException(
                    "Не найден файл questions.txt"
            );
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            List<String> lines = reader.lines().toList();

            List<String> data = new ArrayList<>();

            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    data.add(line.trim());
                }
            }

            if (data.size() % LINES_PER_QUESTION != 0) {
                throw new IllegalArgumentException(
                        "Неверный формат файла questions.txt"
                );
            }

            for (int i = 0; i < data.size(); i += LINES_PER_QUESTION) {

                String text = data.get(i);

                String[] options = {
                        data.get(i + 1),
                        data.get(i + 2),
                        data.get(i + 3),
                        data.get(i + 4)
                };

                int correctOption = Integer.parseInt(data.get(i + 5));

                if (correctOption < 1 || correctOption > 4) {
                    throw new IllegalArgumentException(
                            "Номер ответа должен быть от 1 до 4"
                    );
                }

                Difficulty difficulty = Difficulty.fromNumber(
                        Integer.parseInt(data.get(i + 6))
                );

                String hint = data.get(i + 7);
                String explanation = data.get(i + 8);

                Topic topic = Topic.byQuestionNumber(questions.size() + 1);

                questions.add(
                        new Question(
                                text,
                                options,
                                correctOption,
                                topic,
                                difficulty,
                                hint,
                                explanation
                        )
                );
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Не удалось прочитать questions.txt",
                    e
            );
        }
    }

    @Override
    public int getTotalQuestions() {
        return questions.size();
    }

    @Override
    public Question getQuestionByIndex(int index) {

        if (index < 0 || index >= questions.size()) {
            return null;
        }

        return questions.get(index);
    }
}
