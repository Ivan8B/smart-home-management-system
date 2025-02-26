package home.automation.utils.decimal;

import home.automation.utils.decimal.D_F;

/* Temperature Decimal Formatter */
public class TD_F {
    public static String format(Float temperature) {
        return D_F.format(temperature) + " C°";
    }
}
