package com.evolt.teamecon.economy;

import com.evolt.teamecon.market.DemandState;
import com.evolt.teamecon.market.MarketParams;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MoneyMathTest {
    @Test void fractionalSaleIncomeSurvivesSplitOrders() {
        UUID scope=UUID.randomUUID();
        DemandState market=new DemandState(new MarketParams(.01,.05,30,5000,true));
        long now=1000000, paid=0; double remainder=0;
        double batch=256*market.factorIntegral(scope,"diamond",64,now);
        for(int i=0;i<64;i++) {
            MoneyMath.Settlement sale=MoneyMath.settle(256*market.factorIntegral(scope,"diamond",1,now),remainder);
            paid+=sale.points(); remainder=sale.remainder(); market.addDip(scope,"diamond",1,now);
        }
        assertEquals(MoneyMath.settle(batch,0).points(),paid);
        assertEquals(MoneyMath.settle(batch,0).remainder(),remainder,1e-8);
    }
    @Test void canonicalDemandUnitsDoNotMultiplyBlockPricesTwice() {
        assertEquals(MoneyMath.saleValue(8,8.6,1),MoneyMath.saleValue(72,8.6,9));
    }
    @Test void creditsAndPurchasesStayWithinBounds() {
        assertEquals(MoneyMath.MAX_MONEY,MoneyMath.add(MoneyMath.MAX_MONEY-1,100));
        assertEquals(0,MoneyMath.total(Long.MAX_VALUE,64));
        assertEquals(0,MoneyMath.total(10,-1));
        assertEquals(0,MoneyMath.total(10,Integer.MAX_VALUE));
        assertEquals(11,MoneyMath.buyPrice(7,1.5));
        assertEquals(0,MoneyMath.payout(10,Double.NaN));
    }
    @Test void dustAccumulatesInsteadOfRoundingEverySaleUp() {
        var a=MoneyMath.settle(.4,0); var b=MoneyMath.settle(.4,a.remainder()); var c=MoneyMath.settle(.4,b.remainder());
        assertEquals(0,a.points()); assertEquals(0,b.points()); assertEquals(1,c.points()); assertEquals(.2,c.remainder(),1e-9);
    }
}
