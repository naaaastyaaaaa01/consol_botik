
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

    private static final int LINES_PER_QUESTION = 10;

    private final List<Question> questions = new ArrayList<>();

    public InMemoryQuestionProvider() {
        loadQuestions("/questions_easy.txt", Difficulty.EASY);
        loadQuestions("/questions_medium.txt", Difficulty.MEDIUM);
        loadQuestions("/questions_hard.txt", Difficulty.HARD);
    }

    private void loadQuestions(String fileName, Difficulty expectedDifficulty) {

        InputStream inputStream = getClass().getResourceAsStream(fileName);

        if (inputStream == null) {
            throw new RuntimeException("Не найден файл " + fileName);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            List<String> data = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();

            if (data.size() % LINES_PER_QUESTION != 0) {
                throw new IllegalArgumentException(
                        "Неверный формат файла " + fileName
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

                if (difficulty != expectedDifficulty) {
                    throw new IllegalArgumentException(
                            "Неверная сложность вопроса в файле " + fileName
                    );
                }

                String hint = data.get(i + 7);
                String explanation = data.get(i + 8);

                int originalQuestionNumber = Integer.parseInt(data.get(i + 9));

                Topic topic = Topic.byQuestionNumber(originalQuestionNumber);

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
                    "Не удалось прочитать файл " + fileName,
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
