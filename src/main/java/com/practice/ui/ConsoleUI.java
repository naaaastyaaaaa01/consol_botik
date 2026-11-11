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
    private void printWelcomeMessage() {
        System.out.println("========================================");
        System.out.println("       ВИКТОРИНА ПО ЛИНЕЙНОЙ АЛГЕБРЕ");
        System.out.println("========================================");
        System.out.println();

        System.out.println("Привет!");
        System.out.println("Я показываю вопросы в формате карточек.");
        System.out.println("У каждой карточки есть 4 варианта ответа.");
        System.out.println("Введи номер правильного варианта: 1, 2, 3 или 4.");
        System.out.println();
        System.out.println("Сначала выберешь тему и сложность.");
        System.out.println("Команда /hint — подсказка к вопросу.");
        System.out.println("Команда /review — повторить ошибки.");
        System.out.println("Команда /restart — начать заново.");
        System.out.println("Команда /help — помощь.");
        System.out.println("Команда exit — выход.");
        System.out.println();
    }
    public void start() {

        printWelcomeMessage();

        String response =
                logic.processInput(USER_ID, "");

        System.out.println("Бот: " + response);
        System.out.println();

        // цикл идёт до exit: после завершения доступны /review и /restart
        while (true) {

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
    }
}