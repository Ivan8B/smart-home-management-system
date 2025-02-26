package home.automation.utils.decimal;

import java.text.DecimalFormat;

/* Decimal Formatter */
public class D_F {
    private static final DecimalFormat df = new DecimalFormat("#.#");

    public static String format(Float value) {
        if (value == null) {
            return "-";
        }
        return df.format(value);
    }
}
