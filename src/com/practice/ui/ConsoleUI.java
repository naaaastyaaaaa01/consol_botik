package com.practice.ui;

import com.practice.logic.DialogLogic;

import java.util.Scanner;

public class ConsoleUI {

    private static final String USER_ID = "console_user";

    private final DialogLogic logic;
    private final Scanner scanner = new Scanner(System.in);

    public ConsoleUI(DialogLogic logic) {
        this.logic = logic;
    }

    public void start() {

        System.out.println("========================================");
        System.out.println("       ВИКТОРИНА ПО ЛИНЕЙНОЙ АЛГЕБРЕ");
        System.out.println("========================================");
        System.out.println();

        System.out.println("Привет!");
        System.out.println("Я показываю вопросы в формате карточек.");
        System.out.println("У каждой карточки есть 4 варианта ответа.");
        System.out.println("Введи номер правильного варианта: 1, 2, 3 или 4.");
        System.out.println();
        System.out.println("Команда /help — помощь.");
        System.out.println("Команда exit — выход.");
        System.out.println();

        String response =
                logic.processInput(USER_ID, "");

        System.out.println("Бот: " + response);
        System.out.println();

        while (!logic.isFinished(USER_ID)) {

            System.out.print("Вы: ");

            String input = scanner.nextLine();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Бот: Пока!");
                return;
            }

            response =
                    logic.processInput(USER_ID, input);

            System.out.println();
            System.out.println("Бот: " + response);
            System.out.println();
        }

        System.out.println("Спасибо за игру!");
    }
}