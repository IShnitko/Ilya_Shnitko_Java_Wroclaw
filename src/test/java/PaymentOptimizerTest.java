
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.shnitko.PaymentOptimizer;
import org.shnitko.model.Order;
import org.shnitko.model.PaymentMethod;

import java.io.*;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentOptimizer.
 * <p>
 * This test suite verifies:
 * 1) JSON parsing for orders and payment methods.
 * 2) Correct computation of maximum possible discount per order.
 * 3) End-to-end execution using the greedy algorithm, matching expected usage output.
 */
class PaymentOptimizerTest {
    private static List<Order> orders;
    private static List<PaymentMethod> methods;
    private static Map<String, PaymentMethod> methodMap;

    @BeforeAll
    static void setup() throws IOException {
        // Paths to test resource files
        Path ordersJson = Path.of("src/test/resources/data/orders.json");
        Path methodsJson = Path.of("src/test/resources/data/paymentmethods.json");
        // Load data via public API methods
        orders = PaymentOptimizer.readOrders(ordersJson.toString());
        methods = PaymentOptimizer.readPaymentMethods(methodsJson.toString());
        // Map payment methods by ID for quick lookup
        methodMap = new HashMap<>();
        for (PaymentMethod pm : methods) methodMap.put(pm.id, pm);
    }

    /**
     * Test that orders.json is parsed correctly:
     * - Number of orders read equals 4.
     * - ORDER1 has expected value and promotions list.
     */
    @Test
    void testReadOrders() {
        assertEquals(4, orders.size(), "Should read four orders");
        Order o1 = orders.stream()
                .filter(o -> o.id.equals("ORDER1"))
                .findFirst().orElse(null);
        assertNotNull(o1, "ORDER1 must be present");
        assertEquals(new BigDecimal("100.00"), o1.value, "ORDER1 value mismatch");
        assertEquals(List.of("mZysk"), o1.promotions, "ORDER1 promotions mismatch");
    }

    /**
     * Test that paymentmethods.json is parsed correctly:
     * - Number of payment methods read equals 3.
     * - PUNKTY has expected discount and limit.
     */
    @Test
    void testReadPaymentMethods() {
        assertEquals(3, methods.size(), "Should read three payment methods");
        PaymentMethod pts = methods.stream()
                .filter(m -> m.id.equals("PUNKTY"))
                .findFirst().orElse(null);
        assertNotNull(pts, "PUNKTY method must be present");
        assertEquals(15, pts.discount, "PUNKTY discount mismatch");
        assertEquals(new BigDecimal("100.00"), pts.limit, "PUNKTY limit mismatch");
    }

    /**
     * Test calculateMaxDiscount for a specific order (ORDER2):
     * - ORDER2 value = 200
     * - BosBankrut gives 5% => 10.00
     * - PUNKTY gives 15% => 30.00
     * - Partial C always 10% => 20.00
     * Expect max = 30.00.
     */
    @Test
    void testCalculateMaxDiscountForOrder2() throws Exception {
        Order order2 = orders.stream()
                .filter(o -> o.id.equals("ORDER2"))
                .findFirst().orElseThrow();
        // Invoke private method via reflection
        Method calc = PaymentOptimizer.class
                .getDeclaredMethod("calculateMaxDiscount", Order.class, Map.class);
        calc.setAccessible(true);
        BigDecimal result = (BigDecimal) calc.invoke(null, order2, methodMap);
        assertEquals(new BigDecimal("30.00"), result.setScale(2),
                "Max discount for ORDER2 should be 30.00");
    }

}
