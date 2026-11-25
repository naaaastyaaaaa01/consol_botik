
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

        if (text.equalsIgnoreCase("/start")) {
            session.setStage(UserSession.Stage.CHOOSE_TOPIC);
            return getTopicMenu();
        }

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

        if (text.equalsIgnoreCase("exit")) {
            session.setStage(UserSession.Stage.FINISHED);
            return "Ты завершил викторину. До встречи!\n\n"
                    + getFinishedHint(session);
        }

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

    // ================= СОСТОЯНИЕ ДЛЯ TELEGRAM =================

    public boolean isChoosingTopic(String userId) {
        UserSession session = sessions.get(userId);

        return session != null
                && session.getStage() == UserSession.Stage.CHOOSE_TOPIC;
    }

    public boolean isChoosingDifficulty(String userId) {
        UserSession session = sessions.get(userId);

        return session != null
                && session.getStage() == UserSession.Stage.CHOOSE_DIFFICULTY;
    }

    // Возвращает варианты ответов текущего вопроса для Telegram-кнопок
    public String[] getCurrentOptions(String userId) {

        UserSession session = sessions.get(userId);

        if (session == null || !isAwaitingAnswer(userId)) {
            return new String[0];
        }

        Question question = questionProvider.getQuestionByIndex(
                session.getCurrentProviderIndex()
        );

        return question.getOptions().clone();
    }

    // ================= ВЫБОР ТЕМЫ И СЛОЖНОСТИ =================

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
                + "💡 Подсказка к вопросу — /hint.\n\n"
                + formatCurrent(session);
    }

    // ================= ФИЛЬТРАЦИЯ ВОПРОСОВ =================

    private List<Integer> selectQuestions(
            Topic topic,
            Difficulty difficulty
    ) {

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

    // ================= ПОВТОРЕНИЕ ОШИБОК =================

    private String startReview(UserSession session) {

        if (session.getWrongQuestions().isEmpty()) {
            return "Ошибок для повторения пока нет.";
        }

        session.startRound(
                new ArrayList<>(session.getWrongQuestions()),
                true
        );

        return "🔁 Повторение ошибок. Вопросов: "
                + session.getQueue().size() + ".\n\n"
                + formatCurrent(session);
    }

    // ================= ОТВЕТЫ И ПОДСКАЗКИ =================

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
            return "Пожалуйста, выбери вариант ответа кнопкой ниже "
                    + "или введи номер: 1, 2, 3 или 4.";
        }

        if (answer < 1 || answer > question.getOptions().length) {
            return "Пожалуйста, введи номер ответа от 1 до "
                    + question.getOptions().length + ".";
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
            result = "✅ <b>Верно!</b>";
        } else {
            result = "❌ <b>Неверно.</b>\n"
                    + "<b>Правильный ответ:</b> "
                    + question.getCorrectOption();
        }

        if (!question.getExplanation().isEmpty()) {
            result += "\n<b>📖 Объяснение:</b> "
                    + question.getExplanation();
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

    // ================= ФОРМАТИРОВАНИЕ КАРТОЧКИ =================

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
            boolean reviewMode
    ) {

        StringBuilder result = new StringBuilder();

        result.append("<b>📋 КАРТОЧКА ")
                .append(questionIndex + 1)
                .append(" / ")
                .append(total)
                .append("</b>\n");

        result.append("━━━━━━━━━━━━━━━━━━━\n\n");

        if (reviewMode) {
            result.append("🔁 <b>Повторение ошибок</b>\n");
        }

        if (question.getTopic() != null) {
            result.append("<b>📚 Тема:</b> ")
                    .append(question.getTopic().getTitle())
                    .append("\n");
        }

        if (question.getDifficulty() != null) {
            result.append("<b>🎯 Сложность:</b> ")
                    .append(question.getDifficulty().getTitle())
                    .append("\n");
        }

        result.append("\n<b>❓ Вопрос:</b>\n\n");
        result.append(question.getText()).append("\n\n");

        String[] options = question.getOptions();

        // Если хотя бы один вариант длиннее лимита кнопки,
        // показываем все варианты в карточке полностью.
        if (!allOptionsFitInButtons(options)) {

            result.append("<b>💡 Варианты ответов:</b>\n\n");

            String[] answerEmojis = {
                    "1️⃣", "2️⃣", "3️⃣", "4️⃣"
            };

            for (int i = 0; i < options.length; i++) {

                String emoji = i < answerEmojis.length
                        ? answerEmojis[i]
                        : (i + 1) + ".";

                result.append(emoji)
                        .append(" ")
                        .append(options[i])
                        .append("\n\n");
            }
        }

        result.append("👇 <b>Выбери правильный ответ кнопкой ниже.</b>\n");
        result.append("Ваш ответ: ");

        return result.toString();
    }

    // Проверяет, помещаются ли все варианты в Telegram-кнопки
    private boolean allOptionsFitInButtons(String[] options) {

        for (String option : options) {
            if (option.length() > 64) {
                return false;
            }
        }

        return true;
    }

    // ================= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =================

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
                + "Выбрать ответ можно нажатием на кнопку с его текстом.\n"
                + "После ответа я объясняю правильный вариант.\n\n"
                + "Команды:\n"
                + "/help — показать эту справку\n"
                + "/hint — подсказка к текущему вопросу\n"
                + "/review — повторить вопросы, в которых были ошибки\n"
                + "/restart — начать заново (выбор темы и сложности)\n"
                + "exit — завершить игру.";
    }

    // ================= МЕТОДЫ ДЛЯ СИСТЕМЫ ЖАЛОБ =================

    public boolean isAwaitingAnswer(String userId) {

        UserSession session = sessions.get(userId);

        return session != null
                && session.getStage() == UserSession.Stage.ANSWERING
                && !session.isQueueFinished();
    }

    public int getCurrentQuestionIndex(String userId) {

        UserSession session = sessions.get(userId);

        if (session == null || !isAwaitingAnswer(userId)) {
            return -1;
        }

        return session.getCurrentProviderIndex();
    }

    public String getQuestionText(int providerIndex) {

        if (providerIndex < 0
                || providerIndex >= questionProvider.getTotalQuestions()) {
            return null;
        }

        Question question =
                questionProvider.getQuestionByIndex(providerIndex);

        return question.getText();
    }
}
