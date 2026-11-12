package com.practice.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DialogLogic {

    private final QuestionProvider questionProvider;
    private final Map<String, UserSession> sessions = new HashMap<>();

    public DialogLogic(QuestionProvider questionProvider) {
        this.questionProvider = questionProvider;
    }

    public String processInput(String userId, String input) {

        UserSession session = sessions.computeIfAbsent(
                userId,
                id -> new UserSession()
        );

        String text = input == null ? "" : input.trim();

        // ----- команды, доступные в любой момент -----

        if (text.equalsIgnoreCase("/help")) {
            return getHelpMessage();
        }

        if (text.equalsIgnoreCase("/restart")) {
            session.setStage(UserSession.Stage.CHOOSE_TOPIC);
            return getTopicMenu();
        }

        if (text.equalsIgnoreCase("/review")) {
            return startReview(session);
        }

        // ----- обработка по текущему этапу -----

        switch (session.getStage()) {

            case START:
                session.setStage(UserSession.Stage.CHOOSE_TOPIC);
                return getTopicMenu();

            case CHOOSE_TOPIC:
                return handleTopicChoice(session, text);

            case CHOOSE_DIFFICULTY:
                return handleDifficultyChoice(session, text);

            case ANSWERING:
                return handleAnswering(session, text);

            default:
                return "Викторина завершена.\n"
                        + getFinishedHint(session);
        }
    }

    // ================= выбор темы и сложности =================

    private String getTopicMenu() {

        StringBuilder sb = new StringBuilder();
        sb.append("Выбери тему:\n");
        sb.append("0. Все темы\n");

        Topic[] topics = Topic.values();
        for (int i = 0; i < topics.length; i++) {
            sb.append(i + 1).append(". ")
                    .append(topics[i].getTitle()).append("\n");
        }

        sb.append("\nНомер темы: ");
        return sb.toString();
    }

    private String getDifficultyMenu() {

        StringBuilder sb = new StringBuilder();
        sb.append("Выбери сложность:\n");
        sb.append("0. Любая\n");

        Difficulty[] levels = Difficulty.values();
        for (int i = 0; i < levels.length; i++) {
            sb.append(i + 1).append(". ")
                    .append(levels[i].getTitle()).append("\n");
        }

        sb.append("\nНомер сложности: ");
        return sb.toString();
    }

    private String handleTopicChoice(UserSession session, String text) {

        Topic[] topics = Topic.values();
        int choice = parseNumber(text);

        if (choice < 0 || choice > topics.length) {
            return "Пожалуйста, введи номер темы от 0 до "
                    + topics.length + ".";
        }

        session.setTopic(choice == 0 ? null : topics[choice - 1]);
        session.setStage(UserSession.Stage.CHOOSE_DIFFICULTY);

        return getDifficultyMenu();
    }

    private String handleDifficultyChoice(UserSession session, String text) {

        Difficulty[] levels = Difficulty.values();
        int choice = parseNumber(text);

        if (choice < 0 || choice > levels.length) {
            return "Пожалуйста, введи номер сложности от 0 до "
                    + levels.length + ".";
        }

        session.setDifficulty(choice == 0 ? null : levels[choice - 1]);

        List<Integer> queue = selectQuestions(
                session.getTopic(),
                session.getDifficulty()
        );

        if (queue.isEmpty()) {
            session.setStage(UserSession.Stage.CHOOSE_TOPIC);
            return "Вопросов с такими параметрами нет. Попробуй другие.\n\n"
                    + getTopicMenu();
        }

        session.startRound(queue, false);

        return "Выбрано: " + describeParams(session)
                + ". Вопросов: " + queue.size() + ".\n"
                + "Подсказка к вопросу — /hint.\n\n"
                + formatCurrent(session);
    }

    /** Индексы вопросов, подходящих под тему и сложность (null = любая). */
    private List<Integer> selectQuestions(Topic topic, Difficulty difficulty) {

        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < questionProvider.getTotalQuestions(); i++) {
            Question q = questionProvider.getQuestionByIndex(i);

            boolean topicOk = topic == null || q.getTopic() == topic;
            boolean levelOk = difficulty == null
                    || q.getDifficulty() == difficulty;

            if (topicOk && levelOk) {
                result.add(i);
            }
        }

        return result;
    }

    private String describeParams(UserSession session) {
        String topic = session.getTopic() == null
                ? "все темы" : session.getTopic().getTitle();
        String level = session.getDifficulty() == null
                ? "любая сложность" : session.getDifficulty().getTitle();
        return topic + ", " + level;
    }

    // ================= повторение ошибок =================

    private String startReview(UserSession session) {

        if (session.getWrongQuestions().isEmpty()) {
            return "Ошибок для повторения пока нет.";
        }

        session.startRound(new ArrayList<>(session.getWrongQuestions()), true);

        return "Повторение ошибок. Вопросов: "
                + session.getQueue().size() + ".\n\n"
                + formatCurrent(session);
    }

    // ================= ответы и подсказки =================

    private String handleAnswering(UserSession session, String text) {

        Question question = questionProvider.getQuestionByIndex(
                session.getCurrentProviderIndex()
        );

        if (text.equalsIgnoreCase("/hint")
                || text.equalsIgnoreCase("подсказка")) {
            return "💡 Подсказка: " + question.getHint()
                    + "\n\nВаш ответ: ";
        }

        int answer;

        try {
            answer = Integer.parseInt(text);
        } catch (Exception e) {
            return "Пожалуйста, введи номер ответа: 1, 2, 3 или 4.";
        }

        if (answer < 1 || answer > 4) {
            return "Пожалуйста, введи номер ответа от 1 до 4.";
        }

        boolean correct = question.isCorrect(answer);
        int providerIndex = session.getCurrentProviderIndex();

        if (correct) {
            session.incrementCorrectCount();
            session.getWrongQuestions().remove(providerIndex);
        } else {
            session.getWrongQuestions().add(providerIndex);
        }

        session.incrementQuestionIndex();

        String result;

        if (correct) {
            result = "✓ Верно!";
        } else {
            result = "✗ Неверно.\n"
                    + "Правильный ответ: "
                    + question.getCorrectOption();
        }

        if (!question.getExplanation().isEmpty()) {
            result += "\n📖 Объяснение: " + question.getExplanation();
        }

        if (session.isQueueFinished()) {
            session.setStage(UserSession.Stage.FINISHED);
            return result + "\n\n" + getSummary(session);
        }

        return result + "\n\n" + formatCurrent(session);
    }

    private String getSummary(UserSession session) {

        String title = session.isReviewMode()
                ? "🎉 Повторение завершено!"
                : "🎉 Викторина завершена!";

        return title + "\n"
                + "Правильных ответов: " + session.getCorrectCount()
                + " из " + session.getQueue().size() + "\n\n"
                + getFinishedHint(session);
    }

    private String getFinishedHint(UserSession session) {

        StringBuilder sb = new StringBuilder();
        int wrong = session.getWrongQuestions().size();

        if (wrong > 0) {
            sb.append("Вопросов с ошибками: ").append(wrong)
                    .append(". /review — повторить их.\n");
        }

        sb.append("/restart — начать заново (выбор темы и сложности).\n");
        sb.append("exit — выход.");

        return sb.toString();
    }

    // ================= форматирование =================

    private String formatCurrent(UserSession session) {

        Question question = questionProvider.getQuestionByIndex(
                session.getCurrentProviderIndex()
        );

        return formatQuestion(
                question,
                session.getCurrentQuestionIndex(),
                session.getQueue().size(),
                session.isReviewMode()
        );
    }

    private String formatQuestion(
            Question question,
            int questionIndex,
            int total,
            boolean reviewMode) {

        StringBuilder result = new StringBuilder();

        result.append("┌──────────────────────────────┐\n");
        result.append("│      КАРТОЧКА ")
                .append(questionIndex + 1)
                .append(" / ")
                .append(total)
                .append("       │\n");
        result.append("└──────────────────────────────┘\n");

        if (reviewMode) {
            result.append("[Повторение ошибок]\n");
        }

        if (question.getTopic() != null && question.getDifficulty() != null) {
            result.append("Тема: ").append(question.getTopic().getTitle())
                    .append(" | Сложность: ")
                    .append(question.getDifficulty().getTitle())
                    .append("\n");
        }

        result.append("\n").append(question.getText()).append("\n\n");

        String[] options = question.getOptions();

        for (int i = 0; i < options.length; i++) {
            result.append(i + 1)
                    .append(". ")
                    .append(options[i])
                    .append("\n");
        }

        result.append("\nВаш ответ: ");

        return result.toString();
    }

    /** Разбирает целое число, при ошибке возвращает -1. */
    private int parseNumber(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return -1;
        }
    }

    public boolean isFinished(String userId) {

        UserSession session = sessions.get(userId);

        if (session == null) {
            return false;
        }

        return session.getStage() == UserSession.Stage.FINISHED;
    }

    private String getHelpMessage() {

        return "Я бот-викторина по линейной алгебре.\n\n"
                + "Сначала ты выбираешь тему и сложность, затем я показываю карточки с вопросами.\n"
                + "В каждой карточке 4 варианта ответа.\n"
                + "Тебе нужно ввести номер правильного ответа: 1, 2, 3 или 4.\n"
                + "После ответа я объясняю правильный вариант.\n\n"
                + "Команды:\n"
                + "/help — показать эту справку\n"
                + "/hint — подсказка к текущему вопросу\n"
                + "/review — повторить вопросы, в которых были ошибки\n"
                + "/restart — начать заново (выбор темы и сложности)\n"
                + "exit — завершить игру.";
    }
}
