
package com.practice.telegram;

import com.practice.data.InMemoryQuestionProvider;
import com.practice.logic.DialogLogic;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class TelegramMain {

    public static void main(String[] args) {



        InMemoryQuestionProvider provider =
                new InMemoryQuestionProvider();

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
