package com.evolt.teamecon.scratch;

import java.util.Random;

/** Eight physical tickets, with independent prices, layouts and risk profiles. */
public enum ScratchKind {
    MATCH("match", 10, 0xFFD6A16B, 0),
    DICE("dice", 25, 0xFF86BCB1, 0),
    FRUIT("fruit", 50, 0xFFF193A6, 1),
    SEVENS("sevens", 100, 0xFFDFBE64, 1),
    GEMS("gems", 250, 0xFF6DC9DD, 1),
    BINGO("bingo", 500, 0xFFAE94E3, 2),
    VAULT("vault", 1000, 0xFF6BA4D9, 2),
    CROWN("crown", 2500, 0xFFFFD365, 3);

    private static final int[][] WEIGHTS = {{4200,3800,1500,450,50}, {5650,2400,1350,450,130,20},
            {8005,1200,500,240,50,5}, {9100,500,250,120,25,5}};
    private static final int[][] MULTIPLIERS = {{0,1,2,5,10}, {0,1,2,5,10,30}, {0,1,3,10,50,200}, {0,1,5,20,50,500}};
    private final String id;
    private final long price;
    private final int color, risk;
    ScratchKind(String id, long price, int color, int risk) { this.id=id; this.price=price; this.color=color; this.risk=risk; }
    public String id() { return id; }
    public long price() { return price; }
    public int color() { return color; }
    public int[] multipliers() { return MULTIPLIERS[risk].clone(); }
    public int[] weights() { return WEIGHTS[risk].clone(); }
    public int multiplier(int bucket) { return MULTIPLIERS[risk][Math.clamp(bucket,0,MULTIPLIERS[risk].length-1)]; }
    public double expectedFactor() {
        double value=0; for(int i=0;i<WEIGHTS[risk].length;i++) value+=WEIGHTS[risk][i]*MULTIPLIERS[risk][i]/10000D; return value;
    }
    public int draw(Random random) {
        int roll=random.nextInt(10000); for(int i=0;i<WEIGHTS[risk].length;i++){roll-=WEIGHTS[risk][i];if(roll<0)return i;} return 0;
    }
    public static ScratchKind byId(String id) { for(var kind:values())if(kind.id.equals(id))return kind;return null; }
}
