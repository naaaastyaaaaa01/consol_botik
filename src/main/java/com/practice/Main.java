package com.practice;

import com.practice.data.InMemoryQuestionProvider;
import com.practice.logic.DialogLogic;
import com.practice.ui.ConsoleUI;

public class Main {
    public static void main(String[] args) {
        var provider = new InMemoryQuestionProvider();
        var logic = new DialogLogic(provider);
        var ui = new ConsoleUI(logic);
        ui.start();
    }
}