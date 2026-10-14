import com.practice.data.InMemoryQuestionProvider;
import com.practice.logic.DialogLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogLogicTest {

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

        String result = logic.processInput("user1", "старт");

        assertNotNull(result);
        assertTrue(result.contains(
                "Что такое линейно зависимый набор векторов?"
        ));
    }

    // 3. Проверяем, что у разных пользователей отдельный прогресс
    @Test
    void sessionsOfDifferentUsersAreIndependent() {
        DialogLogic logic = new DialogLogic(new InMemoryQuestionProvider());
        String user1Response = logic.processInput("user1", "старт");
        String user2Response = logic.processInput("user2", "старт");
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
}