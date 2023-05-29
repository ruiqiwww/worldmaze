package byow.Core;

import byow.TileEngine.TETile;

import java.util.Random;

public class MainTest {
    public static void main(String[] args) {
        Engine e = new Engine();
        Random rand = new Random();

        //TETile[][] world = e.interactWithInputString(args[0]);
        //e.render(world);

        //e.startGameLoop();

        e.interactWithKeyboard();
    }
}
