// src/main/java/org/shnitko/PaymentOptimizer.java
package org.shnitko;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.shnitko.model.Order;
import org.shnitko.model.PaymentMethod;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main entry point for payment optimization. Reads orders and payment methods,
 * computes optimal assignment of payment methods to maximize discounts,
 * and outputs usage of each payment method.
 */
public class PaymentOptimizer {

    public static void main(String[] args) throws IOException {
        // Validate arguments: paths to orders.json and paymentmethods.json
        if (args.length < 2) {
            System.err.println("Usage: java -jar optimizer.jar <orders.json> <paymentmethods.json>");
            System.exit(1);
        }

        // Parse input files into domain objects
        List<Order> orders = readOrders(args[0]);
        List<PaymentMethod> methods = readPaymentMethods(args[1]);

        // Map for quick lookup of payment methods by ID
        Map<String, PaymentMethod> methodMap = methods.stream()
                .collect(Collectors.toMap(pm -> pm.id, pm -> pm));

        // Track remaining budget (limit) for each payment method
        Map<String, BigDecimal> remainingLimits = new HashMap<>();
        methods.forEach(pm -> remainingLimits.put(pm.id, pm.limit));

        // Sort orders by descending maximum possible discount
        orders.sort((o1, o2) -> calculateMaxDiscount(o2, methodMap)
                .compareTo(calculateMaxDiscount(o1, methodMap)));

        // Process each order, choosing the best valid payment option
        Set<String> processed = new HashSet<>();
        for (Order order : orders) {
            if (processed.contains(order.id)) continue;

            List<PaymentOption> options = new ArrayList<>();
            addOptionA(order, methodMap, remainingLimits, options);
            addOptionB(order, methodMap, remainingLimits, options);
            addOptionC(order, methodMap, remainingLimits, methods, options);

            // Sort options: highest discount first, then most points used
            options.sort((a, b) -> {
                int cmp = b.discount.compareTo(a.discount);
                return (cmp != 0) ? cmp : b.pointsUsed.compareTo(a.pointsUsed);
            });

            // Apply the first feasible option
            for (PaymentOption opt : options) {
                if (applyPaymentOption(order, opt, remainingLimits)) {
                    processed.add(order.id);
                    break;
                }
            }
        }

        // Print usage of payment methods that were used
        methods.forEach(pm -> {
            BigDecimal used = pm.limit.subtract(remainingLimits.get(pm.id));
            if (used.compareTo(BigDecimal.ZERO) > 0) {
                System.out.printf("%s %.2f\n", pm.id,
                        used.setScale(2, RoundingMode.HALF_UP));
            }
        });
    }

    /**
     * Option A: Use promotional payment methods if budget allows.
     */
    private static void addOptionA(
            Order order,
            Map<String, PaymentMethod> map,
            Map<String, BigDecimal> limits,
            List<PaymentOption> opts
    ) {
        if (order.promotions == null) return;
        for (String promo : order.promotions) {
            PaymentMethod pm = map.get(promo);
            if (pm == null) continue;
            BigDecimal discount = order.value
                    .multiply(BigDecimal.valueOf(pm.discount))
                    .divide(BigDecimal.valueOf(100));
            BigDecimal needed = order.value.subtract(discount);
            if (limits.get(promo).compareTo(needed) >= 0) {
                opts.add(new PaymentOption(OptionType.A, promo, discount, BigDecimal.ZERO));
            }
        }
    }

    /**
     * Option B: Pay entirely with points ("PUNKTY").
     */
    private static void addOptionB(
            Order order,
            Map<String, PaymentMethod> map,
            Map<String, BigDecimal> limits,
            List<PaymentOption> opts
    ) {
        PaymentMethod pts = map.get("PUNKTY");
        if (pts == null) return;
        BigDecimal discount = order.value
                .multiply(BigDecimal.valueOf(pts.discount))
                .divide(BigDecimal.valueOf(100));
        BigDecimal needed = order.value.subtract(discount);
        if (limits.get("PUNKTY").compareTo(needed) >= 0) {
            opts.add(new PaymentOption(OptionType.B, "PUNKTY", discount, needed));
        }
    }

    /**
     * Option C: Combine 10% points + the best available credit card.
     */
    private static void addOptionC(
            Order order,
            Map<String, PaymentMethod> map,
            Map<String, BigDecimal> limits,
            List<PaymentMethod> methods,
            List<PaymentOption> opts
    ) {
        PaymentMethod pts = map.get("PUNKTY");
        if (pts == null) return;
        BigDecimal minPts = order.value.multiply(BigDecimal.valueOf(0.1));
        BigDecimal availPts = limits.get("PUNKTY");
        if (availPts.compareTo(minPts) < 0) return;
        BigDecimal total90 = order.value.multiply(BigDecimal.valueOf(0.9));
        BigDecimal ptsUsed = total90.min(availPts).max(minPts);
        BigDecimal remainder = total90.subtract(ptsUsed);
        Optional<PaymentMethod> best = methods.stream()
                .filter(m -> !m.id.equals("PUNKTY"))
                .filter(m -> limits.get(m.id).compareTo(remainder) >= 0)
                .max(Comparator.comparing(m -> m.discount));
        if (best.isPresent()) {
            BigDecimal discount = order.value.multiply(BigDecimal.valueOf(0.1));
            opts.add(new PaymentOption(OptionType.C, best.get().id, discount, ptsUsed));
        }
    }

    /**
     * Attempt to apply a chosen payment option, updating budgets if successful.
     */
    private static boolean applyPaymentOption(
            Order order,
            PaymentOption opt,
            Map<String, BigDecimal> limits
    ) {
        BigDecimal discount = opt.discount;
        BigDecimal points = opt.pointsUsed;
        BigDecimal orderVal = order.value;
        // Deduct used points
        if (points.signum() > 0) {
            BigDecimal left = limits.get("PUNKTY");
            if (left.compareTo(points) < 0) return false;
            limits.put("PUNKTY", left.subtract(points));
        }
        // Deduct payment from chosen method
        BigDecimal toPay = orderVal.subtract(discount).subtract(points);
        BigDecimal leftCard = limits.get(opt.paymentMethodId);
        if (leftCard.compareTo(toPay) < 0) return false;
        limits.put(opt.paymentMethodId, leftCard.subtract(toPay));
        return true;
    }

    /**
     * Compute the maximum possible discount for sorting orders.
     */
    private static BigDecimal calculateMaxDiscount(
            Order order,
            Map<String, PaymentMethod> map
    ) {
        BigDecimal max = BigDecimal.ZERO;
        // Points-only
        PaymentMethod pts = map.get("PUNKTY");
        if (pts != null) {
            BigDecimal d = order.value.multiply(BigDecimal.valueOf(pts.discount))
                    .divide(BigDecimal.valueOf(100));
            max = max.max(d);
        }
        // Promotional cards
        if (order.promotions != null) {
            for (String promo : order.promotions) {
                PaymentMethod pm = map.get(promo);
                if (pm != null) {
                    BigDecimal d = order.value.multiply(BigDecimal.valueOf(pm.discount))
                            .divide(BigDecimal.valueOf(100));
                    max = max.max(d);
                }
            }
        }
        // 10% partial points
        BigDecimal partial = order.value.multiply(BigDecimal.valueOf(0.1));
        return max.max(partial);
    }

    // JSON readers
    public static List<Order> readOrders(String path) throws IOException {
        return new ObjectMapper().readValue(new File(path),
                new TypeReference<>() {
                });
    }
    public static List<PaymentMethod> readPaymentMethods(String path) throws IOException {
        return new ObjectMapper().readValue(new File(path),
                new TypeReference<>() {
                });
    }

    // Internal data structure for payment option
    private static class PaymentOption {
        OptionType type;
        String paymentMethodId;
        BigDecimal discount;
        BigDecimal pointsUsed;

        PaymentOption(OptionType type, String id, BigDecimal discount, BigDecimal pointsUsed) {
            this.type = type;
            this.paymentMethodId = id;
            this.discount = discount;
            this.pointsUsed = pointsUsed;
        }
    }

    private enum OptionType { A, B, C }
}
