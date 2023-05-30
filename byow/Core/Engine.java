package byow.Core;

import byow.TileEngine.TERenderer;
import byow.TileEngine.TETile;
import byow.TileEngine.Tileset;
import edu.princeton.cs.algs4.In;
import edu.princeton.cs.algs4.StdDraw;
import edu.princeton.cs.algs4.Out;

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Engine {
    TERenderer ter = new TERenderer();

    // for extra fanciness
    private static final boolean DEMOMODE = false;
    private static final int WAITROOM = 3;
    private static final int WAITHALLWAY = 30;
    private static final int WAITSPACE = 1;

    /* Feel free to change the width and height. */
    public static final int WIDTH = 70;
    public static final int HEIGHT = 40;
    private static final int TILE_SIZE = 16;

    // increase this value for extra housing
    private static final int ROOMDENSITY = 30;

    // world gen parameters
    private static final int MINROOMSIZE = 4;
    private static final int MAXROOMSIZE = 7;
    private static final double HALLWAYTURNINGCHANCE = 0.1;
    private static final int MINROOMCONNECTION = 1;
    private static final int MAXROOMCONNECTION = 2;
    private static final int BORDERPROTECT = 2;

    private static final int NUM_LIGHTS = 5;

    // default tile sets
    private static final TETile OBSTACLE = Tileset.WALL;

    private static final TETile INTERIOR = Tileset.FLOOR;
    private static final TETile OUTERWILDS = Tileset.NOTHING;
    private static final TETile PLAYERDEFAULT = Tileset.AVATAR;

    private static final char KEYRIGHT = 'd';
    private static final char KEYLEFT = 'a';
    private static final char KEYUP = 'w';
    private static final char KEYDOWN = 's';

    //Fonts
    private final int REGULAR_SIZE = 2;
    private final Font REGULAR = new Font("Monaco", Font.BOLD, TILE_SIZE * REGULAR_SIZE);
    private final Font REGULAR_SMALLER = new Font("Monaco", Font.BOLD, TILE_SIZE * REGULAR_SIZE - REGULAR_SIZE);
    private final int TITLE_SIZE = 3;
    private final Font TITLE = new Font("Monaco", Font.BOLD, TILE_SIZE * TITLE_SIZE);

    //UI Instructions
    private static final String MENU_TITLE = "CS61B: The Maze";
    private static final String[] MENU_OPTIONS = new String[]{
            "New Game (N)", "Load Game (L)", "Change Appearance (A)", "Quit (Q)"};

    private static final String APPEARANCE = "Choose your avatar's appearance: ";
    private static int currAppear = 0;
    private static ArrayList<Character> APPEAR_CHARS = new ArrayList<>();
    private static final String[] APPEAR_OPTIONS = {
            "Default (D)", "Flower (F)", "Wave (W)", "Leaf (L)", "Chinese (C)", "Back to Main Menu (M)"};
    private static final String[] CHARACTER_DESCRIPTION = {
            "Brave", "Warrior", "Maze Explorer", "Parkourer"};
    private static final TETile[] APPEARANCES = {
            Tileset.AVATAR, Tileset.F_AVATAR, Tileset.W_AVATAR, Tileset.L_AVATAR, Tileset.C_AVATAR};
    private static final String SEED_INSTR = "Please enter a seed of numbers (end with 'S')";

    private static final String[] ESCAPE = {"Where's the way out? ", "need to find the key to unlock the door! ",
    "Got the key to unlock the door! ", "Press (U) to unlock the door! ", "Escape through the door! "};

    private static final String[] LIGHT_INSTRS = {"Too dark! Go find a flashlight!", "Press (B) for light switch"};

    private static final String END = "CONGRATULATIONS!!! YOU WIN!!!";

    private static final String SAVEFILENAME = "./byow/Core/save.txt";

    private static final int LIGHTRADIUS = 5;

    private int currentDescription = 0;

    private TETile[][] world;
    private TETile[][] worldDark;
    private TETile[][] worldTemp;

    private CommonVectors commonVectors = new CommonVectors();

    private ArrayList<Room> rooms;
    private ArrayList<ArrayList<Integer>> adjList = new ArrayList<>(); // adjacent list of all edges
    private ArrayList<ArrayList<Integer>> disList = new ArrayList<>(); // distance of each edge
    private ArrayList<ArrayList<PosVector>> vecList = new ArrayList<>();
    private ArrayList<ArrayList<Integer>> connectedList = new ArrayList<>();

    private DisjointSet ds;

    private long seed = 0;
    Random rand = new Random();

    private TETile player = Tileset.AVATAR;
    private PosVector playerPos = new PosVector();

    private PosVector doorPos = new PosVector();


    private String inputString = "";
    private int currentInputIndex = 0;

    //Game states
    private boolean stateMenu = true;
    private boolean stateSeed = false;
    private boolean stateAppear = false;
    private boolean stateGame = false;
    private boolean stateDark = true;

    private boolean hasKey = false;
    private boolean seenDoor = false;

    private boolean flashlight = false;

    private boolean unlocked = false;

    private boolean gameEnd = false;


    public Engine() {
        ter.initialize(WIDTH, HEIGHT + 1);
    }

    public void render() {
        if (stateDark) {
            lightOut(LIGHTRADIUS);
            ter.renderFrame(worldTemp);
        } else {
            ter.renderFrame(world);
        }
    }

    public void render(long time) {
        render();
        try {
            TimeUnit.MILLISECONDS.sleep(time);
        } catch (InterruptedException e) {

        }
    }

    public void render(TETile[][] w) {
        ter.renderFrame(w);
    }

    public void init() {
        rooms = new ArrayList<>();
        world = new TETile[WIDTH][HEIGHT];
        worldDark = new TETile[WIDTH][HEIGHT];
        commonVectors.movements.add(KEYRIGHT);
        commonVectors.movements.add(KEYUP);
        commonVectors.movements.add(KEYLEFT);
        commonVectors.movements.add(KEYDOWN);
    }

    public void startGameLoop() {
        while(true) {
            render();
            nextInput();
        }
    }

    private char listener() {
        while(!StdDraw.hasNextKeyTyped()) {
            // Its sleepin' time: display the tile description!
            if (stateGame) {
                HUD();
            }
        }
        return Character.toLowerCase(StdDraw.nextKeyTyped());
    }

    /**
     * Method used for exploring a fresh world. This method should handle all inputs,
     * including inputs from the main menu.
     */
    public void interactWithKeyboard() {
        drawMenu();

        while (!stateGame) {
            nextInput();
        }

        startGameLoop();
    }

    private void nextInput() {
        inputString += listener();
        parseInput();
    }

    private void parseInput() {

        char currentChar = inputString.charAt(currentInputIndex);

        if (currentChar == 'q' && currentInputIndex > 0 && inputString.charAt(currentInputIndex - 1) == ':') {
            inputString = inputString.substring(0, currentInputIndex - 1);
            Out out = new Out(SAVEFILENAME);
            out.print();
            out.print(inputString);
            out.close();

            System.exit(0);
        }

        if (stateGame) {
            if (commonVectors.movements.contains(currentChar)) {
                movePlayer(commonVectors.cardinal4[commonVectors.movements.indexOf(currentChar)]);
            }

            if (flashlight && currentChar == 'b') {
                stateDark = !stateDark;
            }

            if (currentChar == 'u') {
                if (hasKey && nearDoor(playerPos)) {
                    world[doorPos.xPos][doorPos.yPos] = Tileset.UNLOCKED_DOOR;
                    unlocked = true;
                }
            }
        }

        if (stateMenu) {
            if (currentChar == 'l') {
                player = PLAYERDEFAULT;
                try {
                    In in = new In(SAVEFILENAME);
                    inputString = in.readAll();
                } catch (IllegalArgumentException e) {
                    System.exit(0);
                }
                currentInputIndex = 0;
                interactWithInputString(inputString);
                startGameLoop();
            }

            if (currentChar == 'a') {
                StdDraw.clear(new Color(0, 0, 0));
                drawAppear();
                stateMenu = false;
                stateSeed = false;
                stateGame = false;
                stateAppear = true;
                for (char a : new char[]{'d', 'f', 'w', 'l', 'c'}) {
                    APPEAR_CHARS.add(a);
                }
            }

            if (currentChar == 'q') {
                System.exit(0);
            }

            if (currentChar == 'n') {
                StdDraw.clear(new Color(0, 0, 0));
                drawStringCenter(SEED_INSTR);
                stateSeed = true;
                stateAppear = false;
                stateMenu = false;
                stateGame = false;
            }
        }

        if (stateSeed) {
            if (Character.isDigit(currentChar)) {
                seed = seed * 10 + Long.parseLong(currentChar + "");
                StdDraw.clear(new Color(0, 0, 0));
                drawSeed(Long.toString(seed));
            }

            if (currentChar == 's') {
                world = generate(seed);
                ter.initialize(WIDTH, HEIGHT + 1);
                stateSeed = false;
                stateAppear = false;
                stateMenu = false;
                stateGame = true;
            }
        }

        if (stateAppear) {
            if (APPEAR_CHARS.contains(currentChar)) {
                currAppear = APPEAR_CHARS.indexOf(currentChar);
                player = APPEARANCES[currAppear];
                StdDraw.clear(new Color(0, 0, 0));
                drawAppear();
            }

            if (currentChar == 'm') {
                StdDraw.clear(new Color(0, 0, 0));
                drawMenu();
                stateMenu = true;
                stateAppear = false;
            }
        }



        currentInputIndex++;
    }

    private void lightOut(int radius) {
        worldTemp = new TETile[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x += 1) {
            for (int y = 0; y < HEIGHT; y += 1) {
                worldTemp[x][y] = OUTERWILDS;
            }
        }
        worldTemp[playerPos.xPos][playerPos.yPos] = world[playerPos.xPos][playerPos.yPos];
        for (int i = 0; i < commonVectors.cardinal4.length; i++) {
            lightOutHelper(radius - 1, i, playerPos.sum(commonVectors.cardinal4[i]));
        }
    }

    private void lightOutHelper(int radius, int currentDir, PosVector pos) {
        if (radius <= 0) {
            worldTemp[pos.xPos][pos.yPos] = world[pos.xPos][pos.yPos];
            return;
        }
        lightOutHelper(radius - 1, currentDir, pos.sum(commonVectors.cardinal4[currentDir]));
        for (int i = 0; i <= radius; i++) {
            worldTemp[pos.xPos][pos.yPos] = world[pos.xPos][pos.yPos];
            pos = pos.sum(commonVectors.cardinal4[(currentDir + 1) % commonVectors.cardinal4.length]);
        }
    }

    private void drawStringCenter(String s) {
        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(REGULAR);
        StdDraw.text(WIDTH / 2, HEIGHT / 2, s);
        StdDraw.show();
    }

    private void drawSeed(String s) {
        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(REGULAR);
        StdDraw.text(WIDTH / 2, HEIGHT * 0.75, SEED_INSTR);
        StdDraw.text(WIDTH / 2, HEIGHT / 2, s);
        StdDraw.show();
    }


    private void drawMenu() {
        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(TITLE);
        StdDraw.text(WIDTH / 2, HEIGHT * 0.75, MENU_TITLE);

        StdDraw.setFont(REGULAR_SMALLER);
        StdDraw.text(WIDTH / 2, HEIGHT * 0.75 - REGULAR_SIZE * 1.2, "Featuring: " + player.character() + " the " + CHARACTER_DESCRIPTION[currentDescription]);
        currentDescription = (currentDescription + 1) % CHARACTER_DESCRIPTION.length;

        StdDraw.setFont(REGULAR);
        double h = HEIGHT / 2;
        for (String o : MENU_OPTIONS) {
            StdDraw.text(WIDTH / 2, h, o);
            h -= 1.5 * REGULAR_SIZE;
        }
        StdDraw.show();
    }

    private void drawAppear() {
        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(TITLE);
        StdDraw.text(WIDTH / 2, HEIGHT * 0.8, APPEARANCE);

        StdDraw.setFont(REGULAR);
        double h = HEIGHT * 0.6;
        for (int i = 0; i < APPEAR_OPTIONS.length; i ++) {
            if (i < APPEARANCES.length) {
                APPEARANCES[i].draw(WIDTH * 2 / 3, h - 0.5);
            }
            StdDraw.textLeft(WIDTH / 3, h, APPEAR_OPTIONS[i]);
            if (i == currAppear) {
                StdDraw.text(WIDTH / 3 - REGULAR_SIZE, h, "▲");
            }

            h -= 1.5 * REGULAR_SIZE;
        }
        StdDraw.show();
    }

    private void drawEnd() {
        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(TITLE);
        StdDraw.text(WIDTH / 2, HEIGHT / 2, END);
        StdDraw.show();
    }

    private void HUD() {
        int currX = (int) StdDraw.mouseX();
        int currY = (int) StdDraw.mouseY();

        TETile[][] worldDisplaying;

        if (stateDark) {
            worldDisplaying = worldTemp;
        } else {
            worldDisplaying = world;
        }
        ter.setFrame(worldDisplaying);
        StdDraw.setPenColor(Color.WHITE);
        if (unlocked) {
            StdDraw.text(WIDTH / 2, HEIGHT, ESCAPE[4]);
        } else if(!seenDoor && !hasKey) {
            StdDraw.text(WIDTH / 2, HEIGHT, ESCAPE[0]);
        } else if(seenDoor && !hasKey) {
            StdDraw.text(WIDTH / 2, HEIGHT, ESCAPE[1]);
        } else if(hasKey && nearDoor(playerPos)) {
            StdDraw.text(WIDTH / 2, HEIGHT, ESCAPE[3]);
        } else if(hasKey && !nearDoor(playerPos)) {
            StdDraw.text(WIDTH / 2, HEIGHT, ESCAPE[2]);
        }

        if(!flashlight) {
            StdDraw.textRight(WIDTH - 1, HEIGHT, LIGHT_INSTRS[0]);
        } else if (stateDark) {
            StdDraw.textRight(WIDTH - 1, HEIGHT, LIGHT_INSTRS[1]);
        }



        if (currY < HEIGHT && currX < WIDTH) {
            TETile currTile = worldDisplaying[currX][currY];
            StdDraw.setPenColor(Color.WHITE);
            StdDraw.textLeft(1, HEIGHT, currTile.description());
        }
        StdDraw.show();
    }

    /**
     * Method used for autograding and testing your code. The input string will be a series
     * of characters (for example, "n123sswwdasdassadwas", "n123sss:q", "lwww". The engine should
     * behave exactly as if the user typed these characters into the engine using
     * interactWithKeyboard.
     *
     * Recall that strings ending in ":q" should cause the game to quite save. For example,
     * if we do interactWithInputString("n123sss:q"), we expect the game to run the first
     * 7 commands (n123sss) and then quit and save. If we then do
     * interactWithInputString("l"), we should be back in the exact same state.
     *
     * In other words, running both of these:
     *   - interactWithInputString("n123sss:q")
     *   - interactWithInputString("lww")
     *
     * should yield the exact same world state as:
     *   - interactWithInputString("n123sssww")
     *
     * @param input the input string to feed to your program
     * @return the 2D TETile[][] representing the state of the world
     */
    public TETile[][] interactWithInputString(String input) {
        // passed in as an argument, and return a 2D tile representation of the
        // world that would have been drawn if the same inputs had been given
        // to interactWithKeyboard().
        //
        // See proj3.byow.InputDemo for a demo of how you can make a nice clean interface
        // that works for many different input types.

        inputString = input.toLowerCase();

        while (currentInputIndex < inputString.length()) {
            parseInput();
        }

        return world;
    }

    public TETile[][] generate(long seed) {
        init();
        rand.setSeed(seed);

        // fill the world with WALL
        for (int x = 0; x < WIDTH; x += 1) {
            for (int y = 0; y < HEIGHT; y += 1) {
                world[x][y] = OBSTACLE;
                worldDark[x][y] = OUTERWILDS;
            }
        }

        // generate a room ROOMDENSITY times
        for (int i = 0; i < ROOMDENSITY; i++) {
            int width = RandomUtils.uniform(rand, MINROOMSIZE, MAXROOMSIZE + 1);
            int height = RandomUtils.uniform(rand, MINROOMSIZE, MAXROOMSIZE + 1);
            int xPos = RandomUtils.uniform(rand, BORDERPROTECT, WIDTH - BORDERPROTECT - width);
            int yPos = RandomUtils.uniform(rand, BORDERPROTECT, HEIGHT - BORDERPROTECT - height);

            // test if the room overlapped with existing ones
            Room tempRoom = new Room(xPos, yPos, width, height);
            boolean overlapped = false;
            for (Room r : rooms) {
                if (r.isOverlapping(tempRoom)) {
                    overlapped = true;
                    break;
                }
            }
            if (overlapped) continue;

            // add room to the world
            rooms.add(tempRoom);
            drawRoom(tempRoom);
        }

        ds = new DisjointSet(rooms.size());

        // create adjacent list and distance list for all rooms
        for (int i = 0; i < rooms.size(); i++) {
            int count = 0;
            int maxDistance = Integer.MAX_VALUE;
            int distance;
            int size = RandomUtils.uniform(rand, MINROOMCONNECTION, MAXROOMCONNECTION + 1);
            PosVector vector = new PosVector(0, 0);

            ArrayList<Integer> adj = new ArrayList<>();
            ArrayList<Integer> dis = new ArrayList<>();
            ArrayList<PosVector> vec = new ArrayList<>();
            ArrayList<Integer> conn = new ArrayList<>();

            for (int j = 0; j < rooms.size(); j++) {
                if (rooms.get(j).equals(rooms.get(i))) continue;
                vector = rooms.get(i).vectorTo(rooms.get(j));
                distance = rooms.get(i).distanceTo(rooms.get(j));
                if (distance < maxDistance) {
                    maxDistance = distance;
                    adj.add(count, j);
                    vec.add(count, vector);
                    dis.add(count, distance);
                    if (count >= size && j != rooms.size() - 1) {
                        adj.remove(0);
                        vec.remove(0);
                        dis.remove(0);
                    } else {
                        count++;
                    }
                }
            }

            conn.add(-1);
            adjList.add(i, adj);
            vecList.add(i, vec);
            disList.add(i, dis);
            connectedList.add(i, conn);
        }

        for (int i = 0; i < adjList.size(); i++) {
            for (int j = 0; j < adjList.get(i).size(); j++) {
                ds.union(i, adjList.get(i).get(j));
            }
        }

        // connect rooms with hallways
        for (int i = 0; i < adjList.size(); i++) {
            for (int j = 0; j < adjList.get(i).size(); j++) {
                if (connectedList.get(i).contains(adjList.get(i).get(j))) continue;
                drawHallway(rooms.get(i), rooms.get(adjList.get(i).get(j)));
                connectedList.get(adjList.get(i).get(j)).add(i);
            }
        }

        ArrayList<Integer> roots = ds.roots();

        if (roots.size() > 1) {
            for (int i = 1; i < roots.size(); i++) {
                ds.union(roots.get(0), roots.get(i));
                drawHallway(rooms.get(roots.get(0)), rooms.get(roots.get(i)));
            }
        }

        // remove walls with no adjacent floor present
        boolean validWall = false;
        for (int x = 0; x < WIDTH; x += 1) {
            for (int y = 0; y < HEIGHT; y += 1) {
                if (world[x][y] == OBSTACLE) {
                    PosVector current = new PosVector(x, y);
                    for (PosVector vec : commonVectors.cardinal8) {
                        PosVector summation = current.sum(vec);
                        if (world[summation.xPos][summation.yPos] == INTERIOR) {
                            validWall = true;
                            break;
                        }
                    }
                    if (!validWall) {
                        world[x][y] = OUTERWILDS;
                        if (DEMOMODE) render(WAITSPACE);
                    }
                    validWall = false;
                }
            }
        }

        //generate a locked door
        boolean validDoor = false;
        while(!validDoor) {
            boolean horizon = RandomUtils.bernoulli(rand);
            if (horizon) {
                doorPos = randomRoom().horizonDoor();
                if (world[doorPos.xPos][doorPos.yPos] == OBSTACLE &&
                        world[doorPos.xPos][doorPos.yPos + 1] != OBSTACLE &&
                        world[doorPos.xPos][doorPos.yPos - 1] != OBSTACLE &&
                        world[doorPos.xPos][doorPos.yPos + 1] != world[doorPos.xPos][doorPos.yPos - 1]) {
                    validDoor = true;
                    break;
                }
            } else {
                doorPos = randomRoom().verticalDoor();
                if (world[doorPos.xPos][doorPos.yPos] == OBSTACLE &&
                        world[doorPos.xPos + 1][doorPos.yPos] != OBSTACLE &&
                        world[doorPos.xPos - 1][doorPos.yPos] != OBSTACLE &&
                        world[doorPos.xPos + 1][doorPos.yPos] != world[doorPos.xPos - 1][doorPos.yPos]) {
                    validDoor = true;
                    break;
                }
            }
        }

        world[doorPos.xPos][doorPos.yPos] = Tileset.LOCKED_DOOR;

        //make player
        playerPos = randomRoom().randomPoint();
        drawPlayer();

        //make key
        PosVector keyPos = randomRoom().randomPoint();
        while (keyPos == playerPos) {
            keyPos = randomRoom().randomPoint();
        }
        world[keyPos.xPos][keyPos.yPos] = Tileset.KEY;

        //make flashlights
        for (int i = 0; i < NUM_LIGHTS; i++) {
            PosVector lightPos = randomRoom().randomPoint();
            if (lightPos != playerPos && lightPos != keyPos) {
                world[lightPos.xPos][lightPos.yPos] = Tileset.LIGHT;
            }
        }

        return world;
    }

    private Room randomRoom() {
        return rooms.get(RandomUtils.uniform(rand, 0, rooms.size()));
    }

    private void drawRoom(Room room) {
        for (int x = room.xPos; x < room.xPos + room.width; x += 1) {
            for (int y = room.yPos; y < room.yPos + room.height; y += 1) {
                world[x][y] = INTERIOR;
                if (DEMOMODE) render(WAITROOM);
            }
        }
    }

    private void drawPlayer() {
        world[playerPos.xPos][playerPos.yPos] = player;
    }

    private void movePlayer(PosVector direction) {
        world[playerPos.xPos][playerPos.yPos] = INTERIOR;
        playerPos = playerPos.sum(direction);

        if (world[playerPos.xPos][playerPos.yPos] == Tileset.UNLOCKED_DOOR) {
            world[playerPos.xPos][playerPos.yPos] = player;
            gameEnd = true;
            stateMenu = false;
            stateSeed = false;
            stateGame = false;
            stateAppear = false;

            render();

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }


            StdDraw.clear(new Color(0, 0, 0));


            drawEnd();
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }



            System.exit(0);

        }

        if (world[playerPos.xPos][playerPos.yPos] == OBSTACLE ) {
            playerPos = playerPos.subtract(direction);
        }

        if (world[playerPos.xPos][playerPos.yPos] == Tileset.LOCKED_DOOR) {
            if(!seenDoor) {
                seenDoor = true;
            }
            playerPos = playerPos.subtract(direction);
        }

        if (world[playerPos.xPos][playerPos.yPos] == Tileset.KEY) {
            hasKey = true;
        }

        if (world[playerPos.xPos][playerPos.yPos] == Tileset.LIGHT) {
            flashlight = true;
        }


        world[playerPos.xPos][playerPos.yPos] = player;
    }

    private boolean nearDoor(PosVector pos) {
        for (PosVector d: commonVectors.cardinal4) {
            PosVector tmp = pos.sum(d);
            if (world[tmp.xPos][tmp.yPos] == Tileset.LOCKED_DOOR) {
                return true;
            }
        }
        return false;
    }
    
    private void drawHallway(Room room1, Room room2) {
        PosVector pos1 = room1.randomPoint();
        PosVector pos2 = room2.randomPoint();
        PosVector diff = pos1.vectorTo(pos2);
        int dirX = 0;
        int dirY = 0;

        if (diff.xPos != 0) dirX = diff.xPos / Math.abs(diff.xPos);
        if (diff.yPos != 0) dirY = diff.yPos / Math.abs(diff.yPos);

        PosVector pointer = new PosVector(pos1.xPos, pos1.yPos);
        boolean towardX = RandomUtils.bernoulli(rand);
        boolean isAdj;
        boolean finishedCorrection = false;

        while (!room2.contains(pointer)) {
            isAdj = room1.isAdjacent(pointer);

            if (pointer.xPos == pos2.xPos) {
                finishedCorrection = true;
                towardX = false;
            } else if (pointer.yPos == pos2.yPos) {
                finishedCorrection = true;
                towardX = true;
            } else if (RandomUtils.bernoulli(rand, HALLWAYTURNINGCHANCE)) {
                towardX = !towardX;
            }

            if (finishedCorrection || !isAdj) {
                if (towardX) {
                    pointer.xPos += dirX;
                } else {
                    pointer.yPos += dirY;
                }
            } else {
                pointer.xPos += dirX;
                if (room1.isAdjacent(pointer)) {
                    pointer.xPos -= dirX;
                    pointer.yPos += dirY;
                    if (room1.isAdjacent(pointer)) {
                        pointer.yPos -= dirY;
                    }
                    finishedCorrection = true;
                }
            }
            world[pointer.xPos][pointer.yPos] = INTERIOR;
            if (DEMOMODE) render(WAITHALLWAY);
        }
    }

    private class DisjointSet {
        private int[] indexArray;

        public DisjointSet(int size) {
            indexArray = new int[size];
            for (int i = 0; i < indexArray.length; i++) {
                indexArray[i] = -1;
            }
        }

        public void union(int index1, int index2) {
            int root1 = findRoot(index1);
            int root2 = findRoot(index2);
            if (root1 != root2) {
                if (indexArray[root1] > indexArray[root2]) {
                    indexArray[root2] += indexArray[root1];
                    indexArray[root1] = index2;
                } else {
                    indexArray[root1] += indexArray[root2];
                    indexArray[root2] = index1;
                }
            }
        }

        public int findRoot(int index) {
            if (indexArray[index] < 0) return index;
            return findRoot(indexArray[index]);
        }

        public ArrayList<Integer> roots() {
            ArrayList<Integer> array = new ArrayList<>();
            for (int i = 0; i < indexArray.length; i++) {
                if (indexArray[i] < 0) {
                    array.add(i);
                }
            }
            return array;
        }
    }

    private class Room {
        private int xPos;
        private int yPos;
        private int width;
        private int height;
        
        private Tileset tile;

        public Room(int x, int y, int width, int height) {
            xPos = x;
            yPos = y;
            this.width = width;
            this.height = height;
        }

        public int distanceTo(Room room) {
            return vectorTo(room).sumEuclidean();
        }

        public PosVector vectorTo(Room room) {
            int x = room.xPos - xPos;
            if (x > 0) {
                x -= width;
                if (x < 0) x = 0;
            } else {
                x += room.width;
                if (x > 0) x = 0;
            }
            int y = room.yPos - yPos;
            if (y > 0) {
                y -= height;
                if (y < 0) y = 0;
            } else {
                y += room.height;
                if (y > 0) y = 0;
            }
            return new PosVector(x, y);
        }

        public boolean isOverlapping(Room room) {
            return (xPos + width >= room.xPos && xPos <= room.xPos + room.width &&
                    yPos + height >= room.yPos && yPos <= room.yPos + room.height);
        }
        
        public PosVector randomPoint() {
            return new PosVector(RandomUtils.uniform(rand, xPos, xPos + width),
                    RandomUtils.uniform(rand, yPos, yPos + height));
        }

        public PosVector horizonDoor() {
            int far = RandomUtils.uniform(rand, 0, 2);
            return new PosVector(RandomUtils.uniform(rand, xPos, xPos + width), yPos - 1 + far * (height + 2));
        }

        public PosVector verticalDoor() {
            int far = RandomUtils.uniform(rand, 0, 2);
            return new PosVector(xPos - 1 + far * (width + 2), RandomUtils.uniform(rand, yPos, yPos + height));
        }

        public boolean contains(PosVector vec) {
            return (vec.xPos >= xPos && vec.xPos < xPos + width &&
                    vec.yPos >= yPos && vec.yPos < yPos + height);
        }

        public boolean isAdjacent(PosVector vec) {
            return (!contains(vec) &&
                    vec.xPos >= xPos - 1 && vec.xPos <= xPos + width &&
                    vec.yPos >= yPos - 1 && vec.yPos <= yPos + height);
        }

        public boolean equals(Room room) {
            return (xPos == room.xPos && yPos == room.yPos);
        }
    }

    private class PosVector {
        private int xPos;
        private int yPos;

        public PosVector(int x, int y) {
            xPos = x;
            yPos = y;
        }

        public PosVector() {
            xPos = 0;
            yPos = 0;
        }

        public int sumEuclidean() {
            return (int) Math.sqrt(Math.pow(xPos, 2) + Math.pow(yPos, 2));
        }
        
        public PosVector vectorTo(PosVector vec) {
            return new PosVector(vec.xPos - xPos, vec.yPos - yPos);
        }

        public PosVector sum(PosVector vec) {
            return new PosVector(Math.min(Math.max(0, xPos + vec.xPos), WIDTH - 1),
                    Math.min(Math.max(0, yPos + vec.yPos), HEIGHT - 1));
        }

        public PosVector subtract(PosVector vec) {
            return new PosVector(Math.min(Math.max(0, xPos - vec.xPos), WIDTH - 1),
                    Math.min(Math.max(0, yPos - vec.yPos), HEIGHT - 1));
        }

        public boolean equals(PosVector vec) {
            return (xPos == vec.xPos && yPos == vec.yPos);
        }

        public PosVector clone() {
            return new PosVector(xPos, yPos);
        }
    }

    private class CommonVectors {
        public final PosVector RIGHT = new PosVector(1, 0);
        public final PosVector LEFT = new PosVector(-1, 0);
        public final PosVector UP = new PosVector(0, 1);
        public final PosVector DOWN = new PosVector(0, -1);
        public final PosVector UPRIGHT = new PosVector(1, 1);
        public final PosVector UPLEFT = new PosVector(-1 ,1);
        public final PosVector DOWNRIGHT = new PosVector(1, -1);
        public final PosVector DOWNLEFT = new PosVector(-1, -1);
        public PosVector[] cardinal4 = {RIGHT, UP, LEFT, DOWN};
        public PosVector[] cardinal8 = {RIGHT, LEFT, UP, DOWN, UPRIGHT, UPLEFT, DOWNRIGHT, DOWNLEFT};

        public ArrayList<Character> movements = new ArrayList<>();
    }
}
