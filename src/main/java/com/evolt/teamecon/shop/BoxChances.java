package com.evolt.teamecon.shop;

import com.evolt.teamecon.config.ConfigJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;

/** Exact percentage tickets for the draw; legacy weighted pools keep their original odds. */
public final class BoxChances {
    public static final int DECIMALS=6, TOTAL=100_000_000;
    private BoxChances() {}

    public static int parse(String text) {
        String value=text.trim().replaceFirst("[%％]$", "").trim();
        if(!value.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))throw new IllegalArgumentException("Invalid percentage");
        return units(new BigDecimal(value));
    }
    private static int units(BigDecimal value) {
        if(value.signum()<0||value.compareTo(BigDecimal.valueOf(100))>0||value.stripTrailingZeros().scale()>DECIMALS)
            throw new IllegalArgumentException("Percentage must be 0 to 100, with up to six decimal places");
        return value.movePointRight(DECIMALS).intValueExact();
    }
    public static BigDecimal percent(int units) { return new BigDecimal(format(units)); }
    public static String format(long units) { return BigDecimal.valueOf(units,DECIMALS).stripTrailingZeros().toPlainString(); }
    public static int entryUnits(JsonObject entry) {
        var value=entry.get("chance");
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||entry.has("weight"))
            throw new IllegalArgumentException("Use numeric chance for every entry, without weight");
        return units(value.getAsBigDecimal());
    }
    public static boolean usesPercentages(JsonArray entries) {
        for(var entry:entries)if(entry.getAsJsonObject().has("chance"))return true;
        return false;
    }
    public static long totalUnits(JsonArray entries) {
        if(!usesPercentages(entries))return entries.isEmpty()?0:TOTAL;
        long total=0;for(var entry:entries)total+=entryUnits(entry.getAsJsonObject());return total;
    }
    /** Validates the entire pool before exposing any prize to the random draw. */
    public static int[] weights(JsonArray entries) {
        int[] weights=new int[entries.size()];boolean percentages=usesPercentages(entries);long total=0;
        for(int i=0;i<weights.length;i++){
            JsonObject entry=entries.get(i).getAsJsonObject();
            weights[i]=percentages?entryUnits(entry):(int)ConfigJson.integer(entry,"weight",1,1,1_000_000);
            total+=weights[i];
        }
        if(percentages&&total!=TOTAL)throw new IllegalArgumentException("Prize percentages must total 100%");
        return weights;
    }
    /** Only used when editing odds or prize membership, never on a header-only save. */
    public static JsonArray asPercentages(JsonArray source) {
        if(source.isEmpty()||usesPercentages(source))return source.deepCopy();
        int[] weights=weights(source),allocated=new int[weights.length];
        long total=Arrays.stream(weights).asLongStream().sum(),used=0;
        long[] remainder=new long[weights.length];Integer[] order=new Integer[weights.length];
        for(int i=0;i<weights.length;i++){
            long scaled=(long)weights[i]*TOTAL;
            allocated[i]=(int)(scaled/total);remainder[i]=scaled%total;used+=allocated[i];order[i]=i;
        }
        Arrays.sort(order,Comparator.<Integer>comparingLong(i->remainder[i]).reversed().thenComparingInt(i->i));
        for(int i=0;i<TOTAL-used;i++)allocated[order[i]]++;
        // Keep every positive legacy prize reachable even below the displayed precision.
        for(int i=0;i<allocated.length;i++)if(allocated[i]==0){
            int largest=0;for(int j=1;j<allocated.length;j++)if(allocated[j]>allocated[largest])largest=j;
            allocated[largest]--;allocated[i]=1;
        }
        JsonArray result=source.deepCopy();
        for(int i=0;i<allocated.length;i++){
            JsonObject entry=result.get(i).getAsJsonObject();entry.remove("weight");entry.addProperty("chance",percent(allocated[i]));
        }
        return result;
    }
}
