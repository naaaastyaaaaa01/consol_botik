

import com.practice.data.InMemoryQuestionProvider;
import com.practice.logic.DialogLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogLogicTest {

    // Проходим меню: тема и сложность (0 = любая) и возвращаем первый вопрос
    private String startQuiz(DialogLogic logic, String user, String topic, String level) {
        logic.processInput(user, "старт");
        logic.processInput(user, topic);
        return logic.processInput(user, level);
    }

    // 1. Проверяем, что команда помощи возвращает сообщение
    @Test
    void helpCommandReturnsHelpMessage() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());

        String result = logic.processInput("user1", "/help");

        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("бот"));
    }

    // 2. Проверяем, что бот показывает настоящий вопрос
    @Test
    void botShowsQuestion() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());

        String result = startQuiz(logic, "user1", "0", "0");

        assertNotNull(result);
        assertTrue(result.contains(
                "Что такое линейно зависимый набор векторов?"
        ));
    }

    // 3. Проверяем, что у разных пользователей отдельный прогресс
    @Test
    void sessionsOfDifferentUsersAreIndependent() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());
        String user1Response = startQuiz(logic, "user1", "0", "0");
        String user2Response = startQuiz(logic, "user2", "0", "0");
        assertEquals(user1Response, user2Response);
        String nextQuestion = logic.processInput("user1", "2");
        assertTrue(nextQuestion.contains(
                "Когда набор векторов является линейно независимым?"
        ));
        String user2Answer = logic.processInput("user2", "не число");

        assertTrue(user2Answer.contains(
                "номер ответа"
        ));
    }

    // 4. Первое сообщение — выбор темы
    @Test
    void firstMessageShowsTopicMenu() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());

        String result = logic.processInput("user1", "старт");

        assertTrue(result.contains("Выбери тему"));
        assertTrue(result.contains("Матрицы"));
    }

    // 5. Некорректный выбор темы не ломает диалог
    @Test
    void invalidTopicChoiceAsksAgain() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());
        logic.processInput("user1", "старт");

        String result = logic.processInput("user1", "99");

        assertTrue(result.contains("номер темы"));
    }

    // 6. После темы идёт выбор сложности
    @Test
    void afterTopicShowsDifficultyMenu() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());
        logic.processInput("user1", "старт");

        String result = logic.processInput("user1", "2");

        assertTrue(result.contains("Выбери сложность"));
    }

    // 7. Вопросы выдаются только по выбранной теме
    @Test
    void questionsAreFilteredByTopic() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());

        // тема 2 — «Матрицы», любая сложность: первый вопрос — №12
        String result = startQuiz(logic, "user1", "2", "0");

        assertTrue(result.contains(
                "Чему равен определитель произведения квадратных матриц?"
        ));
        assertTrue(result.contains("КАРТОЧКА 1 / 14"));
    }

    // 8. Вопросы выдаются с учётом сложности
    @Test
    void questionsAreFilteredByDifficulty() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());

        // тема 1, сложный уровень: вопросы 10 и 11
        String result = startQuiz(logic, "user1", "1", "3");

        assertTrue(result.contains("КАРТОЧКА 1 / 2"));
        assertTrue(result.contains("Сложный"));
        assertTrue(result.contains(
                "Какая формула задаёт размерность суммы подпространств?"
        ));
    }

    // 9. Подсказка показывается и не засчитывается как ответ
    @Test
    void hintIsShownWithoutAnswering() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());
        startQuiz(logic, "user1", "0", "0");

        String hint = logic.processInput("user1", "/hint");
        assertTrue(hint.contains("Подсказка"));

        // вопрос всё ещё первый, ответ принимается
        String next = logic.processInput("user1", "2");
        assertTrue(next.contains("Верно"));
        assertTrue(next.contains("КАРТОЧКА 2"));
    }

    // 10. После ответа выдаётся объяснение
    @Test
    void explanationIsShownAfterAnswer() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());
        startQuiz(logic, "user1", "0", "0");

        String result = logic.processInput("user1", "1");

        assertTrue(result.contains("Неверно"));
        assertTrue(result.contains("Правильный ответ: 2"));
        assertTrue(result.contains("Объяснение"));
    }

    // 11. Повторение ошибок: неверно отвеченные вопросы показываются снова
    @Test
    void reviewModeRepeatsWrongQuestions() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());

        // тема 1, сложность «сложный»: два вопроса (10 и 11)
        startQuiz(logic, "user1", "1", "3");
        logic.processInput("user1", "1");              // №10 — ошибка (верно 3)
        String end = logic.processInput("user1", "2"); // №11 — верно

        assertTrue(end.contains("Викторина завершена"));
        assertTrue(end.contains("/review"));

        String review = logic.processInput("user1", "/review");
        assertTrue(review.contains("Повторение ошибок"));
        assertTrue(review.contains("КАРТОЧКА 1 / 1"));
        assertTrue(review.contains(
                "Какая формула задаёт размерность суммы подпространств?"
        ));

        // правильный ответ убирает вопрос из списка ошибок
        String done = logic.processInput("user1", "3");
        assertTrue(done.contains("Повторение завершено"));
        assertFalse(done.contains("/review"));
    }

    // 12. Если ошибок нет, повторять нечего
    @Test
    void reviewWithoutMistakes() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());
        startQuiz(logic, "user1", "0", "0");

        String result = logic.processInput("user1", "/review");

        assertTrue(result.contains("нет"));
    }

    // 13. /restart возвращает к выбору темы
    @Test
    void restartReturnsToTopicMenu() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());
        startQuiz(logic, "user1", "0", "0");

        String result = logic.processInput("user1", "/restart");

        assertTrue(result.contains("Выбери тему"));
    }
}
