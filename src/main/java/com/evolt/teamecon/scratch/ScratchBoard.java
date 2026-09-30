package com.evolt.teamecon.scratch;

import java.util.Arrays;
import java.util.Random;

/** Deterministic printed boards. The visible rules independently reproduce the paid prize. */
public final class ScratchBoard {
    private static final int[][] LINES = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
    private final ScratchKind kind;
    private final int[] values, prizes;
    private final int target;

    private ScratchBoard(ScratchKind kind, int[] values, int[] prizes, int target) {
        this.kind=kind; this.values=values; this.prizes=prizes; this.target=target;
    }
    public static ScratchBoard create(ScratchTicket ticket) { return create(ticket.kind(),ticket.bucket(),ticket.seed()); }
    public static ScratchBoard create(ScratchKind kind, int bucket, long seed) {
        if (bucket<0 || bucket>=kind.multipliers().length) throw new IllegalArgumentException("Invalid prize bucket");
        Random random=new Random(seed);
        int multiplier=kind.multiplier(bucket), target=random.nextInt(90)+10;
        int size=switch(kind){case MATCH,DICE->6;case GEMS,CROWN->12;default->9;};
        int[] values=new int[size], prizes=new int[size];
        int[] outcomes=kind.multipliers();
        for(int i=0;i<size;i++) prizes[i]=outcomes[1+random.nextInt(outcomes.length-1)];
        switch(kind) {
            case MATCH -> {
                for(int i=0;i<size;i++) { do {values[i]=random.nextInt(90)+10;} while(values[i]==target); }
                if(multiplier>0){int i=random.nextInt(size);values[i]=target;prizes[i]=multiplier;}
            }
            case DICE -> {
                for(int i=0;i<3;i++) {
                    values[i*2]=1+random.nextInt(6);
                    do{values[i*2+1]=1+random.nextInt(6);}while(values[i*2]+values[i*2+1]==7);
                }
                if(multiplier>0){int row=random.nextInt(3);values[row*2+1]=7-values[row*2];prizes[row]=multiplier;}
            }
            case FRUIT -> {
                for(int row=0;row<3;row++) {
                    for(int col=0;col<3;col++) values[row*3+col]=random.nextInt(6);
                    if(values[row*3]==values[row*3+1]&&values[row*3+1]==values[row*3+2])values[row*3+2]=(values[row*3]+1)%6;
                }
                if(multiplier>0){int row=random.nextInt(3);Arrays.fill(values,row*3,row*3+3,random.nextInt(6));prizes[row]=multiplier;}
            }
            case SEVENS -> {
                for(int i=0;i<size;i++){int number=1+random.nextInt(8);values[i]=number>=7?number+1:number;}
                if(multiplier>0){int i=random.nextInt(size);values[i]=7;prizes[i]=multiplier;}
            }
            case GEMS,CROWN -> {
                int count=multiplier==0?random.nextInt(3):bucket+2;
                for(int i=0;i<count;i++)values[i]=1;
                for(int i=size-1;i>0;i--){int j=random.nextInt(i+1),v=values[i];values[i]=values[j];values[j]=v;}
            }
            case BINGO -> {
                // There are only 512 possible grids; choose among ones with exactly the intended number of lines.
                int expectedLines=multiplier>0?1:0;
                do{for(int i=0;i<size;i++)values[i]=random.nextBoolean()?1:0;}while(lineCount(values)!=expectedLines);
                if(multiplier>0)prizes[0]=multiplier;
            }
            case VAULT -> {
                target=random.nextInt(1000);
                for(int row=0;row<3;row++){
                    int code;do{code=random.nextInt(1000);}while(code==target);
                    digits(values,row,code);
                }
                if(multiplier>0){int row=random.nextInt(3);digits(values,row,target);prizes[row]=multiplier;}
            }
        }
        return new ScratchBoard(kind,values,prizes,target);
    }
    private static void digits(int[] values,int row,int code){values[row*3]=code/100;values[row*3+1]=code/10%10;values[row*3+2]=code%10;}
    private static int lineCount(int[] values){int count=0;for(int[] line:LINES)if(values[line[0]]+values[line[1]]+values[line[2]]==3)count++;return count;}
    public int size(){return values.length;}
    public int value(int index){return values[index];}
    public int prize(int index){return prizes[index];}
    public int target(){return target;}
    public int collected(){return Arrays.stream(values).sum();}
    public int multiplier() {
        int total=0;
        switch(kind) {
            case MATCH -> {for(int i=0;i<size();i++)if(values[i]==target)total+=prizes[i];}
            case DICE -> {for(int row=0;row<3;row++)if(values[row*2]+values[row*2+1]==7)total+=prizes[row];}
            case FRUIT -> {for(int row=0;row<3;row++)if(values[row*3]==values[row*3+1]&&values[row*3+1]==values[row*3+2])total+=prizes[row];}
            case SEVENS -> {for(int i=0;i<size();i++)if(values[i]==7)total+=prizes[i];}
            case GEMS,CROWN -> total=collected()<3?0:kind.multiplier(collected()-2);
            case BINGO -> total=lineCount(values)>0?prizes[0]:0;
            case VAULT -> {for(int row=0;row<3;row++)if(values[row*3]*100+values[row*3+1]*10+values[row*3+2]==target)total+=prizes[row];}
        }
        return total;
    }
    public boolean winningCell(int i) {
        return switch(kind) {
            case MATCH -> values[i]==target;
            case DICE -> values[i/2*2]+values[i/2*2+1]==7;
            case FRUIT -> values[i/3*3]==values[i/3*3+1]&&values[i/3*3+1]==values[i/3*3+2];
            case SEVENS -> values[i]==7;
            case GEMS,CROWN -> values[i]==1&&collected()>=3;
            case BINGO -> Arrays.stream(LINES).anyMatch(line->Arrays.stream(line).anyMatch(v->v==i)&&values[line[0]]+values[line[1]]+values[line[2]]==3);
            case VAULT -> values[i/3*3]*100+values[i/3*3+1]*10+values[i/3*3+2]==target;
        };
    }
}
