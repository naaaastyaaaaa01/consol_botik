package com.practice.data;

import com.practice.logic.Question;
import com.practice.logic.QuestionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class InMemoryQuestionProvider implements QuestionProvider {

    private final List<Question> questions = new ArrayList<>();

    public InMemoryQuestionProvider() {

        try {
            List<String> lines =
                    Files.readAllLines(Path.of("src/questions.txt"));

            List<String> data = new ArrayList<>();

            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    data.add(line.trim());
                }
            }

            if (data.size() % 6 != 0) {
                throw new IllegalArgumentException(
                        "Неверный формат файла questions.txt"
                );
            }

            for (int i = 0; i < data.size(); i += 6) {

                String text = data.get(i);

                String[] options = {
                        data.get(i + 1),
                        data.get(i + 2),
                        data.get(i + 3),
                        data.get(i + 4)
                };

                int correctOption =
                        Integer.parseInt(data.get(i + 5));

                if (correctOption < 1 || correctOption > 4) {
                    throw new IllegalArgumentException(
                            "Номер ответа должен быть от 1 до 4"
                    );
                }

                questions.add(
                        new Question(text, options, correctOption)
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