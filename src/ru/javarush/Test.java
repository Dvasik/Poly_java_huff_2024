package ru.javarush;

class Calculator {
    public static void main(String[] args) {
        int a = Integer.valueOf(args[0]);
        int b = Integer.valueOf(args[1]);

        System.out.println(a + b);
    }
}