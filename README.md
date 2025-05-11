# Payment Optimizer

This Java application optimizes the payment strategy for a list of customer orders based on available promotions, payment methods, and loyalty points. It selects the optimal payment method for each order to **maximize total discounts** while respecting individual method limits and business rules.

---

## 🧠 Problem Overview

In our e-supermarket, customers can pay using:

- traditional payment methods (e.g. bank cards),
- loyalty points (`PUNKTY`),
- or a **combo**: paying at least 10% in points gives additional promotion, and the rest with a traditional method.

### Promotions

- Each traditional payment method may offer a discount when the **entire order** is paid using that method.
- If **at least 10%** of an order is paid using loyalty points, a **10% discount** is applied to the whole order.
- If **all** of an order is paid using points, the method-specific `PUNKTY` discount is applied instead.
- **Discounts cannot be stacked.**

---

## 🧾 Input Format

The application expects two JSON files as arguments:

### 1. Orders File

Example: `orders.json`

```json
[
  {
    "id": "ORDER1",
    "value": "100.00",
    "promotions": ["mZysk"]
  },
  {
    "id": "ORDER2",
    "value": "200.00",
    "promotions": ["BosBankrut"]
  }
]
```

- `id`: order ID
- `value`: total order value
- `promotions`: optional list of eligible promotion IDs (correspond to payment method IDs)

### 2. Payment Methods File

Example: `paymentmethods.json`

```json
[
  { "id": "PUNKTY", "discount": "15", "limit": "100.00" },
  { "id": "mZysk", "discount": "10", "limit": "180.00" },
  { "id": "BosBankrut", "discount": "5", "limit": "200.00" }
]
```

- `id`: unique identifier for the payment method
- `discount`: percentage discount (e.g. `10` for 10%)
- `limit`: available amount to spend via this method

---

## ⚙️ How to Build and Run

### ✅ Requirements

- Java 21
- Maven 3.x

### 🔧 Build

```bash
mvn clean package
```

The JAR will be created in the `target/` directory as a **fat-jar** (with dependencies).

### 🚀 Run

```bash
java -jar target/PaymentOptimizer-1.0-SNAPSHOT-jar-with-dependencies.jar \
  src/main/resources/data/orders.json \
  src/main/resources/data/paymentmethods.json
```

### 📤 Output

The program prints the **amount spent** using each method:

```
mZysk 170.00
PUNKTY 100.00
BosBankrut 142.50
```

---

## ✅ Example Scenario

Given:

- ORDER1: 100 PLN, eligible for `mZysk`
- ORDER2: 200 PLN, eligible for `BosBankrut`
- ORDER3: 150 PLN, eligible for both
- ORDER4: 50 PLN, no promotions

Possible optimized output:

```
mZysk 167.50
PUNKTY 100.00
BosBankrut 142.50
```

---

## 🧪 Tests

Unit and integration tests are included using JUnit 5.

Run tests with:

```bash
mvn test
```

Tests include:

- JSON parsing for `Order` and `PaymentMethod`
- Correctness of discount computation
- Integration test verifying final output from `main(...)`

---

## 📌 Project Structure

```
.
├── src/
│   ├── main/java/org/shnitko/        # Main source code
│   ├── main/resources/data           # JSON input files
│   ├── test/java                     # JUnit 5 tests
│   └── test/resources/               # Sample JSON files
├── pom.xml                           # Maven project file
└── README.md                         # This file
```

Project repository also includes build files, which is requiered by task.
