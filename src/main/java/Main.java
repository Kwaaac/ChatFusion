package main.java;

public class Main {
    public static void main(String[] args) {
        int size = 4;
        for (int i = 0; i < size/4; i++) {
            for (int j = 0; j < size / 4; j++) {
                System.out.println("print" + i);
            }
        }
    }
}
