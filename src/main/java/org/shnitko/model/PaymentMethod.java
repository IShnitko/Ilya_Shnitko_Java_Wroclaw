package org.shnitko.model;

import java.math.BigDecimal;

public class PaymentMethod {
    public String id;
    public int discount;
    public BigDecimal limit;

    @Override
    public String toString() {
        return "PaymentMethod{" +
                "id='" + id + '\'' +
                ", discount=" + discount +
                ", limit=" + limit +
                '}';
    }
}
