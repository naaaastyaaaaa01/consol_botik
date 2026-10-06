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
        System.out.println("Бот запущен. /help для справки. exit — выход.");

        while (true) {
            System.out.print("Вы: ");
            String input = scanner.nextLine();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Пока!");
                return;
            }

            String response = logic.processInput(USER_ID, input);
            System.out.println("Бот: " + response);
        }
    }
}