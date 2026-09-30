package com.evolt.teamecon.gambling;

/** A marble lands in one of forty equally likely coloured pockets. Returns include stake. */
public final class ColorWheelGame {
    public record Color(String id, int argb, int multiplier) {}
    public static final Color GREY = new Color("grey", 0xFF303842, 0);
    public static final Color GREEN = new Color("green", 0xFF40C879, 1);
    public static final Color BLUE = new Color("blue", 0xFF499FF4, 2);
    public static final Color PURPLE = new Color("purple", 0xFFB374EA, 4);
    public static final Color GOLD = new Color("gold", 0xFFFFCF4D, 10);
    public static final Color[] COLORS = {GREY, GREEN, BLUE, PURPLE, GOLD};
    public static final int POCKETS = 40;
    private static final Color[] WHEEL = new Color[POCKETS];
    public record Sector(Color color, int weight, int start) {}
    private static final java.util.List<Sector> SECTORS = new java.util.ArrayList<>();
    private static final int[] DISPLAY = new int[POCKETS];
    static {
        java.util.Arrays.fill(WHEEL, GREY);
        for (int i : new int[]{2, 6, 10, 14, 18, 22, 26, 30, 34}) WHEEL[i] = GREEN;
        for (int i : new int[]{4, 12, 20, 28, 36}) WHEEL[i] = BLUE;
        WHEEL[8] = PURPLE; WHEEL[24] = PURPLE; WHEEL[0] = GOLD;
        Color[] order={GOLD,GREY,GREEN,GREY,BLUE,GREY,PURPLE,GREY,GREEN,GREY,BLUE,GREY,GREEN,GREY,PURPLE,GREY,GREEN,GREY,BLUE,GREEN};
        int[] weights={1,3,2,3,2,3,1,3,2,3,2,2,2,2,1,2,2,2,1,1};
        var used=new java.util.HashSet<Integer>();int start=0;
        for(int i=0;i<order.length;i++){
            SECTORS.add(new Sector(order[i],weights[i],start));int n=0;
            for(int pocket=0;pocket<POCKETS&&n<weights[i];pocket++)if(WHEEL[pocket].equals(order[i])&&used.add(pocket))DISPLAY[pocket]=start+n++;
            start+=weights[i];
        }
    }
    public static final double EXPECTED_FACTOR = .925D;
    private ColorWheelGame() {}
    public static Color pocket(int index) { return WHEEL[Math.floorMod(index, POCKETS)]; }
    public static int count(Color color) { return (int) java.util.Arrays.stream(WHEEL).filter(color::equals).count(); }
    public static java.util.List<Sector> sectors() { return java.util.List.copyOf(SECTORS); }
    public static int displayPocket(int pocket) { return DISPLAY[Math.floorMod(pocket,POCKETS)]; }
}
