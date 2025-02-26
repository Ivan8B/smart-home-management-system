package home.automation.utils.decimal;

import home.automation.utils.decimal.D_F;

/* Voltage Decimal Formatter */
public class VD_F {
    public static String format(Float voltage) {
        return D_F.format(voltage) + "V";
    }
}
