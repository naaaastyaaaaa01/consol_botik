
package com.practice.telegram;

import com.practice.data.InMemoryQuestionProvider;
import com.practice.logic.DialogLogic;
import com.practice.logic.Question;
import com.practice.logic.Difficulty;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class TelegramMain {

    public static void main(String[] args) {

        InMemoryQuestionProvider provider =
                new InMemoryQuestionProvider();

        int easy = 0;
        int medium = 0;
        int hard = 0;

        for (int i = 0; i < provider.getTotalQuestions(); i++) {
            Question question = provider.getQuestionByIndex(i);

            if (question.getDifficulty() == Difficulty.EASY) {
                easy++;
            } else if (question.getDifficulty() == Difficulty.MEDIUM) {
                medium++;
            } else if (question.getDifficulty() == Difficulty.HARD) {
                hard++;
            }
        }

        System.out.println("Всего вопросов: "
                + provider.getTotalQuestions());
        System.out.println("Лёгких: " + easy);
        System.out.println("Средних: " + medium);
        System.out.println("Сложных: " + hard);

        DialogLogic logic = new DialogLogic(provider);

        try {
            TelegramBotsApi botsApi =
                    new TelegramBotsApi(DefaultBotSession.class);

            TelegramBot bot = new TelegramBot(logic);

            botsApi.registerBot(bot);

            System.out.println("Telegram-бот успешно запущен!");

        } catch (Exception e) {
            System.out.println("Ошибка запуска бота: "
                    + e.getMessage());
            e.printStackTrace();
        }
    }
}
