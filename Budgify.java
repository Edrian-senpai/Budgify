import javafx.application.Application;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.stage.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.input.KeyCode;

public class Budgify extends Application {

    // Constants
    private static final String CSV_FILE = "expenses.csv";
    private static final String USERS_FILE = "users.csv";
    private static final String[] CATEGORIES = {
        "Housing", "Food", "Transportation", "Utilities", 
        "Healthcare", "Entertainment", "Education", "Savings", 
        "Investments", "Debt", "Other"
    };
    private static final String[] PAYMENT_METHODS = {
        "Cash", "Credit Card", "Debit Card", "Bank Transfer", 
        "Digital Wallet", "Cryptocurrency", "Other"
    };
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    // UI Components
    private TextField amountField, descriptionField, tagsField, searchField;
    private DatePicker datePicker;
    private ComboBox<String> categoryBox, paymentBox, typeBox;
    private TableView<Expense> expenseTable;
    private Label balanceLabel, incomeLabel, expenseLabel;
    private PieChart categoryChart;
    private BarChart<String, Number> monthlyChart;
    private LineChart<String, Number> trendChart;
    private ComboBox<String> pieTypeBox;

    // Data
    private ObservableList<Expense> expenses = FXCollections.observableArrayList();
    private ObservableList<Expense> filteredExpenses = FXCollections.observableArrayList();
    private Map<String, User> users = new HashMap<>();
    private User currentUser = null;

    public static class Expense {
        private final LocalDate date;
        private final String category;
        private final double amount;
        private final String description;
        private final String paymentMethod;
        private final String tags;

        public Expense(LocalDate date, String category, double amount, 
                      String description, String paymentMethod, String tags) {
            this.date = date;
            this.category = category;
            this.amount = amount;
            this.description = description;
            this.paymentMethod = paymentMethod;
            this.tags = tags;
        }

        public LocalDate getDate() { return date; }
        public String getCategory() { return category; }
        public double getAmount() { return amount; }
        public String getDescription() { return description; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getTags() { return tags; }
        public String getFormattedDate() { return date.format(DATE_FORMATTER); }
        public String getFormattedAmount() { return String.format("$%.2f", amount); }
    }

    // Add inside your Budgify class (but outside other methods)
    private static class User {
        String username;
        String password;
        String role; // "admin" or "user"
        User(String username, String password, String role) {
            this.username = username;
            this.password = password;
            this.role = role;
        }
    }

    @Override
    public void start(Stage primaryStage) {
        // Loop until login is successful or cancelled
        while (true) {
            loadUsers();
            boolean loggedIn = showLoginDialog();
            if (!loggedIn) {
                // If login cancelled, close app
                primaryStage.close();
                return;
            }

            // Load expenses for the logged-in user (or all, if not multi-user)
            loadExpenses();
            filteredExpenses.setAll(expenses);

            // --- Build the main UI ---
            primaryStage.setTitle("Budgify - Personal Finance Manager");
            primaryStage.getIcons().add(new Image("file:budgify-icon.png"));

            // Initialize UI components
            createInputForm();
            createExpenseTable();
            createCharts();

            // --- TabPane for all main sections ---
            TabPane mainTabs = new TabPane();
            mainTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            mainTabs.setSide(Side.TOP);
            mainTabs.getStyleClass().add("vertical-tab-pane");

            // --- Sidebar Navigation ---
            VBox navPanel = new VBox(20);
            navPanel.getStyleClass().add("nav-panel");
            navPanel.setAlignment(Pos.TOP_CENTER);

            Label appName = new Label("Budgify");
            appName.getStyleClass().add("app-name");

            Label userInfo = new Label("User: " + currentUser.username + " (" + currentUser.role + ")");
            userInfo.getStyleClass().add("user-info-label");

            Button addTransactionBtn = new Button("Add Transaction");
            addTransactionBtn.getStyleClass().add("nav-button");
            Button categoriesBtn = new Button("Categories");
            categoriesBtn.getStyleClass().add("nav-button");
            Button monthlyBtn = new Button("Monthly");
            monthlyBtn.getStyleClass().add("nav-button");
            Button trendBtn = new Button("Trend");
            trendBtn.getStyleClass().add("nav-button");
            Button reportsBtn = new Button("Reports");
            reportsBtn.getStyleClass().add("nav-button");
            Button logoutBtn = new Button("Logout");
            logoutBtn.getStyleClass().add("nav-button");

            // Navigation actions
            addTransactionBtn.setOnAction(e -> mainTabs.getSelectionModel().select(0));
            categoriesBtn.setOnAction(e -> mainTabs.getSelectionModel().select(1));
            monthlyBtn.setOnAction(e -> mainTabs.getSelectionModel().select(2));
            trendBtn.setOnAction(e -> mainTabs.getSelectionModel().select(3));
            reportsBtn.setOnAction(e -> mainTabs.getSelectionModel().select(4));
            logoutBtn.setOnAction(e -> {
                currentUser = null;
                // Clear the scene and show login again
                primaryStage.getScene().setRoot(new StackPane()); // Hide UI
                start(primaryStage); // Restart login loop
            });

            navPanel.getChildren().addAll(
                appName, userInfo,
                addTransactionBtn, categoriesBtn, monthlyBtn, trendBtn, reportsBtn, logoutBtn
            );

// --- Main Content ---
BorderPane root = new BorderPane();
root.setLeft(navPanel);

// Add Transaction Tab
VBox formBox = new VBox(createInputForm());
formBox.setAlignment(Pos.CENTER);
formBox.setMaxWidth(400);
formBox.setMinWidth(350);
formBox.setPrefWidth(400);

// Make the table fill available space and set a good min height
expenseTable.setMinHeight(300);
expenseTable.setPrefHeight(400);
expenseTable.setMaxHeight(Double.MAX_VALUE); // allow to grow

VBox tableBox = new VBox(15, createTransactionControls(), expenseTable);
tableBox.setAlignment(Pos.TOP_CENTER);
tableBox.setPadding(new Insets(20, 0, 0, 0));
tableBox.setMinHeight(350);
tableBox.setPrefHeight(500);
tableBox.setMaxHeight(Double.MAX_VALUE);
VBox.setVgrow(expenseTable, Priority.ALWAYS); // let table grow

// Center box: switch order so tableBox is above formBox
VBox centerBox = new VBox(40, tableBox, formBox);
centerBox.setAlignment(Pos.TOP_CENTER);
centerBox.setPadding(new Insets(30, 0, 0, 0));
centerBox.setMinWidth(600);
centerBox.setPrefWidth(900);
centerBox.setMaxWidth(Double.MAX_VALUE);
VBox.setVgrow(tableBox, Priority.ALWAYS);

// Wrap centerBox in ScrollPane to avoid overflow
ScrollPane scrollPane = new ScrollPane(centerBox);
scrollPane.setFitToWidth(true); // Make it resize horizontally
scrollPane.setFitToHeight(true); // Resize vertically

StackPane addTransactionPane = new StackPane(scrollPane);
addTransactionPane.setPadding(new Insets(30));
addTransactionPane.setAlignment(Pos.TOP_CENTER);

Tab addTab = new Tab("Add Transaction", addTransactionPane);


            // Chart Tabs
            TabPane chartTabs = createCharts();
            for (Tab t : chartTabs.getTabs()) {
                mainTabs.getTabs().add(t);
            }
            mainTabs.getTabs().add(0, addTab);

            root.setCenter(addTransactionPane);

            // Dashboard cards at the top
            HBox dashboardCards = createDashboardCards();
            dashboardCards.getStyleClass().add("top-panel");
            root.setTop(dashboardCards);

            // Scene setup
            Scene scene = new Scene(root, 1300, 850);
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            scene.setFill(Color.web("#f5f7fa"));

            primaryStage.setScene(scene);
            primaryStage.show();
            updateDashboard();
            updateCharts();

            // Break the login loop after successful login and UI build
            break;
        }
    }

    private VBox createInputForm() {
        VBox form = new VBox(15);
        form.setPadding(new Insets(20));
        form.getStyleClass().add("form-panel");

        Label formTitle = new Label("Add New Transaction");
        formTitle.getStyleClass().add("form-title");

        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(250);
        datePicker.getStyleClass().add("form-control");

        categoryBox = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        categoryBox.setPromptText("Select Category");
        categoryBox.setPrefWidth(250);
        categoryBox.getStyleClass().add("form-control");

        amountField = new TextField();
        amountField.setPromptText("0.00");
        amountField.setPrefWidth(250);
        amountField.getStyleClass().add("form-control");

        descriptionField = new TextField();
        descriptionField.setPromptText("Transaction description");
        descriptionField.setPrefWidth(250);
        descriptionField.getStyleClass().add("form-control");

        paymentBox = new ComboBox<>(FXCollections.observableArrayList(PAYMENT_METHODS));
        paymentBox.setPromptText("Payment Method");
        paymentBox.setPrefWidth(250);
        paymentBox.getStyleClass().add("form-control");

        tagsField = new TextField();
        tagsField.setPromptText("Tags (comma separated)");
        tagsField.setPrefWidth(250);
        tagsField.getStyleClass().add("form-control");

        typeBox = new ComboBox<>(FXCollections.observableArrayList("Income", "Expense"));
        typeBox.setPromptText("Type");
        typeBox.setPrefWidth(250);
        typeBox.getStyleClass().add("form-control");
        typeBox.setValue("Expense");

        Button addButton = new Button("Add Transaction");
        addButton.getStyleClass().add("primary-button");
        addButton.setOnAction(_ -> saveExpense());

        Button clearButton = new Button("Clear");
        clearButton.getStyleClass().add("secondary-button");
        clearButton.setOnAction(_ -> clearForm());

        HBox buttonBox = new HBox(10, addButton, clearButton);
        buttonBox.setAlignment(Pos.CENTER);

        form.getChildren().addAll(
            formTitle,
            createFormRow("Date:", datePicker),
            createFormRow("Category:", categoryBox),
            createFormRow("Amount:", amountField),
            createFormRow("Description:", descriptionField),
            createFormRow("Payment Method:", paymentBox),
            createFormRow("Tags:", tagsField),
            createFormRow("Type:", typeBox),
            buttonBox
        );

        return form;
    }

    private HBox createFormRow(String labelText, Control control) {
        Label label = new Label(labelText);
        label.setMinWidth(100);
        label.getStyleClass().add("form-label");
        return new HBox(10, label, control);
    }

    @SuppressWarnings({ "deprecation", "unchecked", "unused" })
    private TableView<Expense> createExpenseTable() {
        expenseTable = new TableView<>();
        expenseTable.setItems(filteredExpenses);
        expenseTable.setPlaceholder(new Label("No transactions recorded yet"));
        expenseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        expenseTable.getStyleClass().add("expense-table");

        TableColumn<Expense, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cell -> 
            new SimpleStringProperty(cell.getValue().getFormattedDate()));
        dateCol.getStyleClass().add("table-column");

        TableColumn<Expense, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.getStyleClass().add("table-column");

        TableColumn<Expense, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cell -> 
            new SimpleStringProperty(cell.getValue().getFormattedAmount()));
        amountCol.setComparator((s1, s2) -> {
            double d1 = Double.parseDouble(s1.replace("$", "").replace(",", ""));
            double d2 = Double.parseDouble(s2.replace("$", "").replace(",", ""));
            return Double.compare(d1, d2);
        });
        amountCol.getStyleClass().add("table-column");

        TableColumn<Expense, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.getStyleClass().add("table-column");

        TableColumn<Expense, String> paymentCol = new TableColumn<>("Payment");
        paymentCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        paymentCol.getStyleClass().add("table-column");

        TableColumn<Expense, String> tagsCol = new TableColumn<>("Tags");
        tagsCol.setCellValueFactory(new PropertyValueFactory<>("tags"));
        tagsCol.getStyleClass().add("table-column");

        TableColumn<Expense, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cell -> 
            new SimpleStringProperty(cell.getValue().getAmount() >= 0 ? "Income" : "Expense"));
        typeCol.getStyleClass().add("table-column");

        expenseTable.getColumns().addAll(dateCol, categoryCol, amountCol, descCol, paymentCol, tagsCol, typeCol);

        // Context menu for table
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> deleteSelected());
        MenuItem editItem = new MenuItem("Edit");
        editItem.setOnAction(e -> editSelected());
        contextMenu.getItems().addAll(editItem, deleteItem);
        expenseTable.setContextMenu(contextMenu);

        // Keyboard navigation
        expenseTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                deleteSelected();
            }
        });

        // Hover effects
        expenseTable.setRowFactory(tv -> {
            TableRow<Expense> row = new TableRow<>();
            row.hoverProperty().addListener((obs) -> {
                if (row.isHover()) {
                    row.setStyle("-fx-background-color: #f5f5f5;");
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });

        return expenseTable;
    }

    @SuppressWarnings("unused")
    private HBox createTransactionControls() {
        HBox controlsContainer = new HBox(15);
        controlsContainer.setAlignment(Pos.CENTER_LEFT);

        // Search bar
        searchField = new TextField();
        searchField.setPromptText("Search transactions...");
        searchField.setPrefWidth(250);
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredExpenses.setAll(expenses.stream()
                .filter(e -> e.getDescription().toLowerCase().contains(newVal.toLowerCase()) ||
                             e.getCategory().toLowerCase().contains(newVal.toLowerCase()) ||
                             e.getTags().toLowerCase().contains(newVal.toLowerCase()))
                .collect(Collectors.toList()));
            updateDashboard();
            updateCharts();
        });

        // Filter controls
        ComboBox<String> filterCategory = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        filterCategory.getItems().add(0, "All Categories");
        filterCategory.setValue("All Categories");
        filterCategory.setPromptText("Filter by category");
        filterCategory.setPrefWidth(180);

        DatePicker fromDate = new DatePicker(LocalDate.now().minusMonths(1));
        fromDate.setPrefWidth(120);

        DatePicker toDate = new DatePicker(LocalDate.now());
        toDate.setPrefWidth(120);

        Button filterButton = new Button("Apply Filters");
        filterButton.getStyleClass().add("filter-button");
        filterButton.setOnAction(e -> applyFilters(
            filterCategory.getValue(),
            fromDate.getValue(),
            toDate.getValue()
        ));

        controlsContainer.getChildren().addAll(
            new Label("Search:"), searchField,
            new Label("Category:"), filterCategory,
            new Label("From:"), fromDate,
            new Label("To:"), toDate,
            filterButton
        );
        return controlsContainer;
    }

    private void saveExpense() {
        LocalDate date = datePicker.getValue();
        String category = categoryBox.getValue();
        String amountText = amountField.getText();
        String description = descriptionField.getText();
        String payment = paymentBox.getValue();
        String tags = tagsField.getText();
        String type = typeBox.getValue();

        if (date == null || category == null || category.isEmpty() ||
            amountText == null || amountText.isEmpty() ||
            payment == null || payment.isEmpty() ||
            type == null || type.isEmpty()) {
            showAlert("Validation Error", "Please fill in all required fields.");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountText);
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Amount must be a valid number.");
            return;
        }
        // Set sign based on type
        if ("Expense".equals(type) && amount > 0) amount = -amount;
        if ("Income".equals(type) && amount < 0) amount = Math.abs(amount);

        Expense expense = new Expense(date, category, amount, description, payment, tags);
        expenses.add(expense);
        saveToFile(expense);
        clearForm();

        // Reload from CSV to ensure table is always in sync
        loadExpenses();
        filteredExpenses.setAll(expenses);
        updateDashboard();
        updateCharts();
    }

    private void clearForm() {
        datePicker.setValue(LocalDate.now());
        categoryBox.getSelectionModel().clearSelection();
        amountField.clear();
        descriptionField.clear();
        paymentBox.getSelectionModel().clearSelection();
        tagsField.clear();
        typeBox.setValue("Expense");
    }

    private void deleteSelected() {
        if (currentUser == null || !"admin".equals(currentUser.role)) {
            showAlert("Permission Denied", "Only admin can delete transactions.");
            return;
        }
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            expenses.remove(selected);
            updateDataFile();

            // Reload from CSV
            loadExpenses();
            filteredExpenses.setAll(expenses);
            updateDashboard();
            updateCharts();
        }
    }

    // Now allows editing and updating a transaction
    private void editSelected() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            datePicker.setValue(selected.getDate());
            categoryBox.setValue(selected.getCategory());
            amountField.setText(String.valueOf(Math.abs(selected.getAmount())));
            descriptionField.setText(selected.getDescription());
            paymentBox.setValue(selected.getPaymentMethod());
            tagsField.setText(selected.getTags());
            typeBox.setValue(selected.getAmount() >= 0 ? "Income" : "Expense");

            expenses.remove(selected);
            updateDataFile();

            // Reload from CSV
            loadExpenses();
            filteredExpenses.setAll(expenses);
            updateDashboard();
            updateCharts();
        } else {
            showAlert("Edit", "Please select a transaction to edit.");
        }
    }

    private void applyFilters(String category, LocalDate from, LocalDate to) {
        filteredExpenses.setAll(expenses.stream()
            .filter(e -> (category.equals("All Categories") || e.getCategory().equals(category)))
            .filter(e -> (from == null || !e.getDate().isBefore(from)))
            .filter(e -> (to == null || !e.getDate().isAfter(to)))
            .collect(Collectors.toList()));
        updateDashboard();
        updateCharts();
    }

    // Dashboard now uses filteredExpenses for live stats
    private void updateDashboard() {
        double totalIncome = filteredExpenses.stream().filter(e -> e.getAmount() > 0).mapToDouble(Expense::getAmount).sum();
        double totalExpense = filteredExpenses.stream().filter(e -> e.getAmount() < 0).mapToDouble(Expense::getAmount).sum();
        double balance = totalIncome + totalExpense;
        if (incomeLabel != null) incomeLabel.setText(String.format("$%.2f", totalIncome));
        if (expenseLabel != null) expenseLabel.setText(String.format("$%.2f", Math.abs(totalExpense)));
        if (balanceLabel != null) balanceLabel.setText(String.format("$%.2f", balance));
    }

    private void updateCharts() {
        if (pieTypeBox != null) updatePieChart(pieTypeBox.getValue());
        if (monthlyChart != null) updateBarChart();
        if (trendChart != null) updateTrendChart();
    }

    private void updateTrendChart() {
        trendChart.getData().clear();

        List<Expense> sorted = new ArrayList<>(filteredExpenses);
        sorted.sort(Comparator.comparing(Expense::getDate));

        Map<String, Double> balanceByDate = new LinkedHashMap<>();
        double runningBalance = 0.0;
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Expense e : sorted) {
            String date = e.getDate().format(dateFmt);
            runningBalance += e.getAmount();
            balanceByDate.put(date, runningBalance);
        }

        XYChart.Series<String, Number> balanceSeries = new XYChart.Series<>();
        balanceSeries.setName("Balance");
        for (Map.Entry<String, Double> entry : balanceByDate.entrySet()) {
            balanceSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }

        trendChart.getData().add(balanceSeries);
    }

    @SuppressWarnings("unchecked")
    private void updateBarChart() {
        monthlyChart.getData().clear();

        Map<String, Double> incomeByMonth = new TreeMap<>();
        Map<String, Double> expenseByMonth = new TreeMap<>();

        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (Expense e : filteredExpenses) {
            String month = e.getDate().format(monthFmt);
            if (e.getAmount() >= 0) {
                incomeByMonth.put(month, incomeByMonth.getOrDefault(month, 0.0) + e.getAmount());
            } else {
                expenseByMonth.put(month, expenseByMonth.getOrDefault(month, 0.0) + Math.abs(e.getAmount()));
            }
        }

        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Income");
        for (String month : incomeByMonth.keySet()) {
            incomeSeries.getData().add(new XYChart.Data<>(month, incomeByMonth.get(month)));
        }

        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Expenses");
        for (String month : expenseByMonth.keySet()) {
            expenseSeries.getData().add(new XYChart.Data<>(month, expenseByMonth.get(month)));
        }

        monthlyChart.getData().addAll(incomeSeries, expenseSeries);
    }

    // Improved CSV loader: skips malformed lines, trims whitespace, logs errors
    private void loadExpenses() {
        expenses.clear();
        File file = new File(CSV_FILE);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(CSV_FILE))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length < 6) {
                    System.err.println("Skipping malformed line " + lineNum + ": " + line);
                    continue;
                }
                try {
                    LocalDate date = LocalDate.parse(parts[0].trim());
                    String category = parts[1].trim();
                    double amount = Double.parseDouble(parts[2].trim());
                    String description = parts[3].trim();
                    String paymentMethod = parts[4].trim();
                    String tags = parts[5].trim();

                    expenses.add(new Expense(
                        date, category, amount,
                        description, paymentMethod, tags
                    ));
                } catch (Exception ex) {
                    System.err.println("Skipping bad data at line " + lineNum + ": " + line);
                }
            }
            filteredExpenses.setAll(expenses);
        } catch (IOException e) {
            showAlert("Error", "Failed to load expenses: " + e.getMessage());
        }
    }

    // Load users from CSV
    private void loadUsers() {
        users.clear();
        File file = new File(USERS_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length >= 3) {
                    users.put(parts[0], new User(parts[0], parts[1], parts[2]));
                }
            }
        } catch (IOException e) {
            showAlert("Error", "Failed to load users: " + e.getMessage());
        }
    }

    private void saveToFile(Expense expense) {
        try (FileWriter writer = new FileWriter(CSV_FILE, true)) {
            writer.write(String.format("%s,%s,%.2f,%s,%s,%s\n",
                expense.getDate(),
                expense.getCategory(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getPaymentMethod(),
                expense.getTags()
            ));
        } catch (IOException e) {
            showAlert("Error", "Failed to save transaction: " + e.getMessage());
        }
    }

    private void saveUser(User user) {
        try (FileWriter writer = new FileWriter(USERS_FILE, true)) {
            writer.write(String.format("%s,%s,%s\n", user.username, user.password, user.role));
        } catch (IOException e) {
            showAlert("Error", "Failed to save user: " + e.getMessage());
        }
    }

    private void updateDataFile() {
        try (FileWriter writer = new FileWriter(CSV_FILE)) {
            for (Expense e : expenses) {
                writer.write(String.format("%s,%s,%.2f,%s,%s,%s\n",
                    e.getDate(),
                    e.getCategory(),
                    e.getAmount(),
                    e.getDescription(),
                    e.getPaymentMethod(),
                    e.getTags()
                ));
            }
        } catch (IOException e) {
            showAlert("Error", "Failed to update data file: " + e.getMessage());
        }
    }

    private void exportCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export CSV");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("Date,Category,Amount,Description,Payment,Tags\n");
                for (Expense e : filteredExpenses) {
                    writer.write(String.format("%s,%s,%.2f,%s,%s,%s\n",
                        e.getDate(),
                        e.getCategory(),
                        e.getAmount(),
                        e.getDescription(),
                        e.getPaymentMethod(),
                        e.getTags()
                    ));
                }
                showAlert("Success", "Data exported to CSV successfully");
            } catch (IOException e) {
                showAlert("Error", "Failed to export CSV: " + e.getMessage());
            }
        }
    }

    private HBox createDashboardCards() {
        balanceLabel = new Label("$0.00");
        balanceLabel.getStyleClass().add("balance-label");
        VBox balanceCard = createDashboardCard("Total Balance", balanceLabel, "balance-card");

        incomeLabel = new Label("$0.00");
        incomeLabel.getStyleClass().add("income-label");
        VBox incomeCard = createDashboardCard("Income", incomeLabel, "income-card");

        expenseLabel = new Label("$0.00");
        expenseLabel.getStyleClass().add("expense-label");
        VBox expenseCard = createDashboardCard("Expenses", expenseLabel, "expense-card");

        HBox cardsBox = new HBox(15, balanceCard, incomeCard, expenseCard);
        cardsBox.setAlignment(Pos.CENTER);
        return cardsBox;
    }

    private VBox createDashboardCard(String title, Node content, String styleClass) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");

        VBox card = new VBox(5, titleLabel, content);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20));
        card.getStyleClass().add(styleClass);
        card.setMinWidth(180);

        return card;
    }

    @SuppressWarnings({ "unchecked", "unused" })
    private TabPane createCharts() {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("chart-tabs");

        // Category Pie Chart
        categoryChart = new PieChart();
        categoryChart.setTitle("By Category");
        categoryChart.getStyleClass().add("chart");

        pieTypeBox = new ComboBox<>(FXCollections.observableArrayList("Expenses", "Income", "All"));
        pieTypeBox.setValue("Expenses");
        pieTypeBox.setPrefWidth(120);
        pieTypeBox.valueProperty().addListener((_, __, newVal) -> updatePieChart(newVal));

        // Totals label
        Label totalsLabel = new Label();
        totalsLabel.setStyle("-fx-font-size: 14px; -fx-padding: 5;");

        // Category breakdown table
        TableView<CategoryBreakdown> breakdownTable = new TableView<>();
        breakdownTable.setPrefHeight(200);

        TableColumn<CategoryBreakdown, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().category));
        TableColumn<CategoryBreakdown, String> incomeCol = new TableColumn<>("Income");
        incomeCol.setCellValueFactory(data -> new SimpleStringProperty(String.format("$%.2f", data.getValue().income)));
        TableColumn<CategoryBreakdown, String> expenseCol = new TableColumn<>("Expense");
        expenseCol.setCellValueFactory(data -> new SimpleStringProperty(String.format("$%.2f", Math.abs(data.getValue().expense))));
        TableColumn<CategoryBreakdown, String> netCol = new TableColumn<>("Net");
        netCol.setCellValueFactory(data -> new SimpleStringProperty(String.format("$%.2f", data.getValue().income + data.getValue().expense)));

        breakdownTable.getColumns().addAll(catCol, incomeCol, expenseCol, netCol);

        VBox pieChartBox = new VBox(10, new Label("Show:"), pieTypeBox, totalsLabel, categoryChart, new Label("Category Breakdown:"), breakdownTable);
        pieChartBox.setAlignment(Pos.TOP_CENTER);
        Tab categoryTab = new Tab("Categories", pieChartBox);
        categoryTab.getStyleClass().add("chart-tab");

        // Monthly Bar Chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        monthlyChart = new BarChart<>(xAxis, yAxis);
        monthlyChart.setTitle("Monthly Overview");
        monthlyChart.getStyleClass().add("chart");
        Tab monthlyTab = new Tab("Monthly", monthlyChart);
        monthlyTab.getStyleClass().add("chart-tab");

        // Trend Line Chart
        CategoryAxis trendXAxis = new CategoryAxis();
        NumberAxis trendYAxis = new NumberAxis();
        trendChart = new LineChart<>(trendXAxis, trendYAxis);
        trendChart.setTitle("Spending Trend");
        trendChart.getStyleClass().add("chart");
        Tab trendTab = new Tab("Trend", trendChart);
        trendTab.getStyleClass().add("chart-tab");

        // Reports Tab
        VBox reportsBox = new VBox(20);
        reportsBox.setPadding(new Insets(15));
        reportsBox.setAlignment(Pos.TOP_CENTER);
        reportsBox.getStyleClass().add("reports-panel");

        // Summary section
        Label summaryLabel = new Label();
        summaryLabel.setStyle("-fx-font-size: 14px; -fx-padding: 10;");

        double totalIncome = expenses.stream().filter(x -> x.getAmount() > 0).mapToDouble(Expense::getAmount).sum();
        double totalExpense = expenses.stream().filter(x -> x.getAmount() < 0).mapToDouble(Expense::getAmount).sum();
        long count = expenses.size();
        summaryLabel.setText(
            "Total Transactions: " + count +
            "\nTotal Income: $" + String.format("%.2f", totalIncome) +
            "\nTotal Expenses: $" + String.format("%.2f", Math.abs(totalExpense)) +
            "\nNet Balance: $" + String.format("%.2f", totalIncome + totalExpense)
        );

        // Transactions Table (read-only)
        TableView<Expense> reportTable = new TableView<>();
        reportTable.setItems(filteredExpenses);
        reportTable.setPlaceholder(new Label("No transactions recorded yet"));
        reportTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<Expense, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFormattedDate()));
        TableColumn<Expense, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        TableColumn<Expense, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFormattedAmount()));
        TableColumn<Expense, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        TableColumn<Expense, String> paymentCol = new TableColumn<>("Payment");
        paymentCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        TableColumn<Expense, String> tagsCol = new TableColumn<>("Tags");
        tagsCol.setCellValueFactory(new PropertyValueFactory<>("tags"));
        TableColumn<Expense, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAmount() >= 0 ? "Income" : "Expense"));

        reportTable.getColumns().addAll(dateCol, categoryCol, amountCol, descCol, paymentCol, tagsCol, typeCol);

        // CSV Export Button
        Button exportCsvBtn = new Button("Export CSV Data");
        exportCsvBtn.getStyleClass().add("report-button");
        exportCsvBtn.setOnAction(e -> exportCSV());

        reportsBox.getChildren().addAll(
            new Label("Summary:"), summaryLabel,
            new Label("All Transactions:"), reportTable,
            exportCsvBtn
        );

        Tab reportsTab = new Tab("Reports", reportsBox);
        reportsTab.getStyleClass().add("chart-tab");

        tabPane.getTabs().addAll(categoryTab, monthlyTab, trendTab, reportsTab);

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateCharts();
        });

        filteredExpenses.addListener((ListChangeListener<Expense>) c -> {
            updatePieChart(pieTypeBox.getValue());
        });

        updatePieChart(pieTypeBox.getValue());

        return tabPane;
    }

    // Helper class for breakdown table
    private static class CategoryBreakdown {
        String category;
        double income;
        double expense;
        CategoryBreakdown(String category, double income, double expense) {
            this.category = category;
            this.income = income;
            this.expense = expense;
        }
    }

    @SuppressWarnings("unchecked")
    private void updatePieChart(String type) {
        categoryChart.getData().clear();

        Map<String, Double> incomeTotals = new LinkedHashMap<>();
        Map<String, Double> expenseTotals = new LinkedHashMap<>();
        for (String cat : CATEGORIES) {
            incomeTotals.put(cat, 0.0);
            expenseTotals.put(cat, 0.0);
        }

        for (Expense e : filteredExpenses) {
            if (e.getAmount() > 0) {
                incomeTotals.put(e.getCategory(), incomeTotals.getOrDefault(e.getCategory(), 0.0) + e.getAmount());
            } else if (e.getAmount() < 0) {
                expenseTotals.put(e.getCategory(), expenseTotals.getOrDefault(e.getCategory(), 0.0) + e.getAmount());
            }
        }

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        double totalIncome = incomeTotals.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalExpense = expenseTotals.values().stream().mapToDouble(Math::abs).sum();

        if ("Expenses".equals(type) || "All".equals(type)) {
            for (String cat : CATEGORIES) {
                double val = Math.abs(expenseTotals.get(cat));
                if (val > 0) {
                    PieChart.Data slice = new PieChart.Data(cat + " (Expense)", val);
                    pieData.add(slice);
                }
            }
        }
        if ("Income".equals(type) || "All".equals(type)) {
            for (String cat : CATEGORIES) {
                double val = incomeTotals.get(cat);
                if (val > 0) {
                    PieChart.Data slice = new PieChart.Data(cat + " (Income)", val);
                    pieData.add(slice);
                }
            }
        }

        categoryChart.setData(pieData);

        // Add tooltips to slices
        for (PieChart.Data data : pieData) {
            double percent = (data.getPieValue() / (totalIncome + totalExpense)) * 100;
            Tooltip.install(data.getNode(), new Tooltip(
                String.format("%s: $%.2f (%.1f%%)", data.getName(), data.getPieValue(), percent)
            ));
        }

        // Show totals above chart
        StringBuilder totals = new StringBuilder();
        if ("Expenses".equals(type) || "All".equals(type)) {
            totals.append("Total Expenses: $").append(String.format("%.2f", totalExpense)).append("  ");
        }
        if ("Income".equals(type) || "All".equals(type)) {
            totals.append("Total Income: $").append(String.format("%.2f", totalIncome));
        }
        VBox pieChartBox = (VBox) categoryChart.getParent();
        for (Node node : pieChartBox.getChildren()) {
            if (node instanceof Label && ((Label) node).getStyle().contains("font-size: 14px")) {
                ((Label) node).setText(totals.toString());
            }
        }

        // Update breakdown table
        TableView<CategoryBreakdown> breakdownTable = null;
        for (Node node : pieChartBox.getChildren()) {
            if (node instanceof TableView) {
                breakdownTable = (TableView<CategoryBreakdown>) node;
                break;
            }
        }
        if (breakdownTable != null) {
            ObservableList<CategoryBreakdown> breakdownData = FXCollections.observableArrayList();
            for (String cat : CATEGORIES) {
                breakdownData.add(new CategoryBreakdown(cat, incomeTotals.get(cat), expenseTotals.get(cat)));
            }
            breakdownTable.setItems(breakdownData);
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Login and Sign Up dialog
    private boolean showLoginDialog() {
        while (true) {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Budgify Login / Sign Up");
            dialog.setHeaderText("Login or Sign Up");

            ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
            ButtonType signupButtonType = new ButtonType("Sign Up", ButtonBar.ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, signupButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField username = new TextField();
            username.setPromptText("Username");
            PasswordField password = new PasswordField();
            password.setPromptText("Password");

            grid.add(new Label("Username:"), 0, 0);
            grid.add(username, 1, 0);
            grid.add(new Label("Password:"), 0, 1);
            grid.add(password, 1, 1);

            dialog.getDialogPane().setContent(grid);

            dialog.setResultConverter(dialogButton -> dialogButton);

            Optional<ButtonType> result = dialog.showAndWait();

            if (!result.isPresent() || result.get() == ButtonType.CANCEL) {
                return false;
            }

            String user = username.getText().trim();
            String pass = password.getText().trim();

            if (result.get() == loginButtonType) {
                if (users.containsKey(user) && users.get(user).password.equals(pass)) {
                    currentUser = users.get(user);
                    showAlert("Login Success", "Welcome, " + user + " (" + currentUser.role + ")");
                    return true;
                } else {
                    showAlert("Login Failed", "Incorrect username or password.");
                }
            } else if (result.get() == signupButtonType) {
                if (user.isEmpty() || pass.isEmpty()) {
                    showAlert("Sign Up Failed", "Username and password required.");
                    continue;
                }
                if (users.containsKey(user)) {
                    showAlert("Sign Up Failed", "Username already exists.");
                    continue;
                }
                // First user is admin, others are users
                String role = users.isEmpty() ? "admin" : "user";
                User newUser = new User(user, pass, role);
                users.put(user, newUser);
                saveUser(newUser);
                showAlert("Sign Up Success", "Account created! Please log in.");
            }
        }
    }
}


