import javafx.animation.*;
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
import javafx.util.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.util.Duration;
import javafx.scene.input.KeyCode;

public class Budgify extends Application {

    // Constants
    private static final String CSV_FILE = "expenses.csv";
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
    private ComboBox<String> categoryBox, paymentBox;
    private TableView<Expense> expenseTable;
    private Label balanceLabel, incomeLabel, expenseLabel;
    private PieChart categoryChart;
    private BarChart<String, Number> monthlyChart;
    private LineChart<String, Number> trendChart;
    
    // Data
    private ObservableList<Expense> expenses = FXCollections.observableArrayList();
    private ObservableList<Expense> filteredExpenses = FXCollections.observableArrayList();

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

        // Getters
        public LocalDate getDate() { return date; }
        public String getCategory() { return category; }
        public double getAmount() { return amount; }
        public String getDescription() { return description; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getTags() { return tags; }
        
        // Formatted getters for display
        public String getFormattedDate() { return date.format(DATE_FORMATTER); }
        public String getFormattedAmount() { return String.format("$%.2f", amount); }
    }

    @Override
    public void start(Stage primaryStage) {
        if (!showLoginDialog()) {
            System.exit(0);
        }

        primaryStage.setTitle("Budgify - Personal Finance Manager");
        primaryStage.getIcons().add(new Image("file:budgify-icon.png"));

        // Initialize UI components
        createInputForm();
        createExpenseTable();
        createDashboard();
        createCharts();

        // Load existing data
        loadExpenses();
        updateDashboard();
        updateCharts();

        // Main layout
        BorderPane root = new BorderPane();
        
        // Left Navigation Panel
        VBox leftNav = createNavigationPanel();
        root.setLeft(leftNav);
        
        // Top: Dashboard with search
        HBox topPanel = createTopPanel();
        root.setTop(topPanel);
        
        // Center: Split between input/table and charts
        SplitPane centerPane = new SplitPane();
        VBox leftPane = new VBox(20, createInputForm(), expenseTable);
        leftPane.setPadding(new Insets(15));
        leftPane.setPrefWidth(500);
        
        TabPane chartTabs = createCharts();
        chartTabs.setPrefWidth(600);
        
        centerPane.getItems().addAll(leftPane, chartTabs);
        centerPane.setDividerPositions(0.45);
        
        root.setCenter(centerPane);
        
        // Bottom: Status bar
        root.setBottom(createStatusBar());

        // Scene setup
        Scene scene = new Scene(root, 1300, 850);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        scene.setFill(Color.web("#f5f7fa"));
        
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createNavigationPanel() {
        VBox navPanel = new VBox(15);
        navPanel.setPadding(new Insets(20));
        navPanel.setPrefWidth(200);
        navPanel.getStyleClass().add("nav-panel");
        
        ImageView logo = new ImageView(new Image("file:budgify-icon.png"));
        logo.setFitWidth(80);
        logo.setFitHeight(80);
        
        Label appName = new Label("Budgify");
        appName.getStyleClass().add("app-name");
        
        VBox menuItems = new VBox(10);
        menuItems.setPadding(new Insets(20, 0, 0, 0));
        
        Button dashboardBtn = createNavButton("Dashboard", "dashboard-icon");
        Button transactionsBtn = createNavButton("Transactions", "transactions-icon");
        Button reportsBtn = createNavButton("Reports", "reports-icon");
        Button settingsBtn = createNavButton("Settings", "settings-icon");
        
        menuItems.getChildren().addAll(dashboardBtn, transactionsBtn, reportsBtn, settingsBtn);
        
        navPanel.getChildren().addAll(logo, appName, menuItems);
        return navPanel;
    }
    
    private Button createNavButton(String text, String iconClass) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        return btn;
    }
    
    private HBox createTopPanel() {
        HBox topPanel = new HBox(20);
        topPanel.setPadding(new Insets(15));
        topPanel.getStyleClass().add("top-panel");
        
        // Search bar
        searchField = new TextField();
        searchField.setPromptText("Search transactions...");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredExpenses.setAll(expenses.stream()
                .filter(e -> e.getDescription().toLowerCase().contains(newVal.toLowerCase()) ||
                             e.getCategory().toLowerCase().contains(newVal.toLowerCase()) ||
                             e.getTags().toLowerCase().contains(newVal.toLowerCase()))
                .collect(Collectors.toList()));
            updateDashboard();
            updateCharts();
        });
        
        // Dashboard cards
        HBox dashboardCards = createDashboard();
        
        topPanel.getChildren().addAll(searchField, dashboardCards);
        topPanel.setAlignment(Pos.CENTER_LEFT);
        return topPanel;
    }

    private VBox createInputForm() {
        VBox form = new VBox(15);
        form.setPadding(new Insets(20));
        form.getStyleClass().add("form-panel");
        
        // Form title
        Label formTitle = new Label("Add New Transaction");
        formTitle.getStyleClass().add("form-title");
        
        // Date
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(250);
        datePicker.getStyleClass().add("form-control");
        
        // Category
        categoryBox = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        categoryBox.setPromptText("Select Category");
        categoryBox.setPrefWidth(250);
        categoryBox.getStyleClass().add("form-control");
        
        // Amount
        amountField = new TextField();
        amountField.setPromptText("0.00");
        amountField.setPrefWidth(250);
        amountField.getStyleClass().add("form-control");
        
        // Description
        descriptionField = new TextField();
        descriptionField.setPromptText("Transaction description");
        descriptionField.setPrefWidth(250);
        descriptionField.getStyleClass().add("form-control");
        
        // Payment Method
        paymentBox = new ComboBox<>(FXCollections.observableArrayList(PAYMENT_METHODS));
        paymentBox.setPromptText("Payment Method");
        paymentBox.setPrefWidth(250);
        paymentBox.getStyleClass().add("form-control");
        
        // Tags
        tagsField = new TextField();
        tagsField.setPromptText("Tags (comma separated)");
        tagsField.setPrefWidth(250);
        tagsField.getStyleClass().add("form-control");
        
        // Buttons
        Button addButton = new Button("Add Transaction");
        addButton.getStyleClass().add("primary-button");
        addButton.setOnAction(e -> saveExpense());
        
        Button clearButton = new Button("Clear");
        clearButton.getStyleClass().add("secondary-button");
        clearButton.setOnAction(e -> clearForm());
        
        HBox buttonBox = new HBox(10, addButton, clearButton);
        buttonBox.setAlignment(Pos.CENTER);
        
        // Form layout
        form.getChildren().addAll(
            formTitle,
            createFormRow("Date:", datePicker),
            createFormRow("Category:", categoryBox),
            createFormRow("Amount:", amountField),
            createFormRow("Description:", descriptionField),
            createFormRow("Payment Method:", paymentBox),
            createFormRow("Tags:", tagsField),
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

    private TableView<Expense> createExpenseTable() {
        expenseTable = new TableView<>();
        expenseTable.setItems(filteredExpenses);
        expenseTable.setPlaceholder(new Label("No transactions recorded yet"));
        expenseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        expenseTable.getStyleClass().add("expense-table");
        
        // Date column
        TableColumn<Expense, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cell -> 
            new SimpleStringProperty(cell.getValue().getFormattedDate()));
        dateCol.getStyleClass().add("table-column");
        
        // Category column
        TableColumn<Expense, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.getStyleClass().add("table-column");
        
        // Amount column with custom formatting
        TableColumn<Expense, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cell -> 
            new SimpleStringProperty(cell.getValue().getFormattedAmount()));
        amountCol.setComparator((s1, s2) -> {
            double d1 = Double.parseDouble(s1.replace("$", "").replace(",", ""));
            double d2 = Double.parseDouble(s2.replace("$", "").replace(",", ""));
            return Double.compare(d1, d2);
        });
        amountCol.getStyleClass().add("table-column");
        
        // Description column
        TableColumn<Expense, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.getStyleClass().add("table-column");
        
        // Payment method column
        TableColumn<Expense, String> paymentCol = new TableColumn<>("Payment");
        paymentCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        paymentCol.getStyleClass().add("table-column");
        
        // Tags column
        TableColumn<Expense, String> tagsCol = new TableColumn<>("Tags");
        tagsCol.setCellValueFactory(new PropertyValueFactory<>("tags"));
        tagsCol.getStyleClass().add("table-column");
        
        expenseTable.getColumns().addAll(dateCol, categoryCol, amountCol, descCol, paymentCol, tagsCol);
        
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

    private HBox createDashboard() {
        // Balance card
        balanceLabel = new Label("$0.00");
        balanceLabel.getStyleClass().add("balance-label");
        VBox balanceCard = createDashboardCard("Total Balance", balanceLabel, "balance-card");
        
        // Income card
        incomeLabel = new Label("$0.00");
        incomeLabel.getStyleClass().add("income-label");
        VBox incomeCard = createDashboardCard("Income", incomeLabel, "income-card");
        
        // Expense card
        expenseLabel = new Label("$0.00");
        expenseLabel.getStyleClass().add("expense-label");
        VBox expenseCard = createDashboardCard("Expenses", expenseLabel, "expense-card");
        
        // Filter controls
        ComboBox<String> filterCategory = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        filterCategory.getItems().add(0, "All Categories");
        filterCategory.setValue("All Categories");
        filterCategory.setPromptText("Filter by category");
        filterCategory.getStyleClass().add("filter-control");
        
        DatePicker fromDate = new DatePicker(LocalDate.now().minusMonths(1));
        fromDate.getStyleClass().add("filter-control");
        DatePicker toDate = new DatePicker(LocalDate.now());
        toDate.getStyleClass().add("filter-control");
        
        Button filterButton = new Button("Apply Filters");
        filterButton.getStyleClass().add("filter-button");
        filterButton.setOnAction(e -> applyFilters(
            filterCategory.getValue(),
            fromDate.getValue(),
            toDate.getValue()
        ));
        
        HBox filterBox = new HBox(10, 
            new Label("Category:"), filterCategory,
            new Label("From:"), fromDate,
            new Label("To:"), toDate,
            filterButton
        );
        filterBox.setAlignment(Pos.CENTER_LEFT);
        filterBox.setPadding(new Insets(10));
        
        // Dashboard layout
        HBox statsBox = new HBox(20, balanceCard, incomeCard, expenseCard);
        statsBox.setAlignment(Pos.CENTER);
        
        // Main dashboard container
        HBox dashboard = new HBox(20, statsBox, filterBox);
        dashboard.setPadding(new Insets(15));
        dashboard.getStyleClass().add("dashboard-container");
        
        return dashboard;
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

    private TabPane createCharts() {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("chart-tabs");
        
        // Category Pie Chart
        categoryChart = new PieChart();
        categoryChart.setTitle("Expense by Category");
        categoryChart.getStyleClass().add("chart");
        Tab categoryTab = new Tab("Categories", categoryChart);
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
        reportsBox.setAlignment(Pos.CENTER);
        reportsBox.getStyleClass().add("reports-panel");
        
        Button exportPdfBtn = new Button("Export PDF Report");
        exportPdfBtn.getStyleClass().add("report-button");
        exportPdfBtn.setOnAction(e -> exportPdfReport());
        
        Button exportCsvBtn = new Button("Export CSV Data");
        exportCsvBtn.getStyleClass().add("report-button");
        exportCsvBtn.setOnAction(e -> exportCSV());
        
        reportsBox.getChildren().addAll(
            new Label("Generate Reports"),
            new HBox(10, exportPdfBtn, exportCsvBtn)
        );
        
        Tab reportsTab = new Tab("Reports", reportsBox);
        reportsTab.getStyleClass().add("chart-tab");
        
        tabPane.getTabs().addAll(categoryTab, monthlyTab, trendTab, reportsTab);
        return tabPane;
    }

    private HBox createStatusBar() {
        Label statusLabel = new Label("Ready");
        Label countLabel = new Label("0 transactions");
        
        // Status indicator
        Circle statusIndicator = new Circle(5, Color.GREEN);
        Tooltip.install(statusIndicator, new Tooltip("System status: OK"));
        
        HBox statusBar = new HBox(15, statusIndicator, statusLabel, countLabel);
        statusBar.setPadding(new Insets(8));
        statusBar.getStyleClass().add("status-bar");
        
        // Update transaction count when data changes
        filteredExpenses.addListener((ListChangeListener<Expense>) c -> {
            countLabel.setText(String.format("%d transactions", filteredExpenses.size()));
        });
        
        return statusBar;
    }

    private void saveExpense() {
        try {
            LocalDate date = datePicker.getValue();
            String category = categoryBox.getValue();
            double amount = Double.parseDouble(amountField.getText());
            String description = descriptionField.getText();
            String paymentMethod = paymentBox.getValue();
            String tags = tagsField.getText();

            if (category == null || amount <= 0) {
                showAlert("Error", "Please enter valid category and amount");
                return;
            }

            Expense newExpense = new Expense(
                date, category, amount, 
                description, paymentMethod, tags
            );

            expenses.add(newExpense);
            saveToFile(newExpense);
            clearForm();
            updateDashboard();
            updateCharts();
            
            // Animate table update
            animateTableUpdate();
            
        } catch (NumberFormatException e) {
            showAlert("Error", "Please enter a valid amount");
        } catch (Exception e) {
            showAlert("Error", "Failed to save transaction: " + e.getMessage());
        }
    }

    private void animateTableUpdate() {
        FadeTransition ft = new FadeTransition(Duration.millis(300), expenseTable);
        ft.setFromValue(0.5);
        ft.setToValue(1.0);
        ft.play();
        
        // Scroll to the new entry
        expenseTable.scrollTo(expenses.size() - 1);
    }

    private void clearForm() {
        datePicker.setValue(LocalDate.now());
        categoryBox.getSelectionModel().clearSelection();
        amountField.clear();
        descriptionField.clear();
        paymentBox.getSelectionModel().clearSelection();
        tagsField.clear();
    }

    private void deleteSelected() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            expenses.remove(selected);
            updateDataFile();
            updateDashboard();
            updateCharts();
        } else {
            showAlert("Error", "Please select a transaction to delete");
        }
    }

    private void editSelected() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // Populate form with selected expense
            datePicker.setValue(selected.getDate());
            categoryBox.setValue(selected.getCategory());
            amountField.setText(String.valueOf(selected.getAmount()));
            descriptionField.setText(selected.getDescription());
            paymentBox.setValue(selected.getPaymentMethod());
            tagsField.setText(selected.getTags());
            
            // Remove the selected expense (will be re-added if saved)
            expenses.remove(selected);
        } else {
            showAlert("Error", "Please select a transaction to edit");
        }
    }

    private void applyFilters(String category, LocalDate fromDate, LocalDate toDate) {
        filteredExpenses.setAll(expenses.stream()
            .filter(e -> category.equals("All Categories") || e.getCategory().equals(category))
            .filter(e -> fromDate == null || !e.getDate().isBefore(fromDate))
            .filter(e -> toDate == null || !e.getDate().isAfter(toDate))
            .collect(Collectors.toList()));
        
        updateDashboard();
        updateCharts();
    }

    private void updateDashboard() {
        double income = filteredExpenses.stream()
            .filter(e -> e.getAmount() > 0)
            .mapToDouble(Expense::getAmount)
            .sum();
        
        double expenses = filteredExpenses.stream()
            .filter(e -> e.getAmount() < 0)
            .mapToDouble(Expense::getAmount)
            .sum();
        
        double balance = income + expenses;
        
        balanceLabel.setText(String.format("$%.2f", balance));
        incomeLabel.setText(String.format("$%.2f", income));
        expenseLabel.setText(String.format("$%.2f", Math.abs(expenses)));
    }

    private void updateCharts() {
        updatePieChart();
        updateBarChart();
        updateTrendChart();
    }

    private void updatePieChart() {
        categoryChart.getData().clear();
        
        filteredExpenses.stream()
            .filter(e -> e.getAmount() < 0) // Only expenses (negative amounts)
            .collect(Collectors.groupingBy(
                Expense::getCategory,
                Collectors.summingDouble(e -> Math.abs(e.getAmount()))
            ))
            .forEach((category, total) -> {
                PieChart.Data slice = new PieChart.Data(
                    String.format("%s (%.2f)", category, total), 
                    total
                );
                categoryChart.getData().add(slice);
            });
    }

    private void updateBarChart() {
        monthlyChart.getData().clear();
        
        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Income");
        
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Expenses");
        
        // Group by month
        Map<String, Double> monthlyIncome = filteredExpenses.stream()
            .filter(e -> e.getAmount() > 0)
            .collect(Collectors.groupingBy(
                e -> e.getDate().getMonth().toString(),
                Collectors.summingDouble(Expense::getAmount)
             ) );



        
        Map<String, Double> monthlyExpenses = filteredExpenses.stream()
            .filter(e -> e.getAmount() < 0)
            .collect(Collectors.groupingBy(
                e -> e.getDate().getMonth().toString(),
                Collectors.summingDouble(e -> Math.abs(e.getAmount()))
            ));
        
        // Add all months to maintain consistent x-axis
        Set<String> allMonths = new TreeSet<>();
        allMonths.addAll(monthlyIncome.keySet());
        allMonths.addAll(monthlyExpenses.keySet());
        
        for (String month : allMonths) {
            incomeSeries.getData().add(new XYChart.Data<>(
                month, 
                monthlyIncome.getOrDefault(month, 0.0)
            ));
            
            expenseSeries.getData().add(new XYChart.Data<>(
                month, 
                monthlyExpenses.getOrDefault(month, 0.0)
            ));
        }
        
        monthlyChart.getData().addAll(incomeSeries, expenseSeries);
    }

    private void updateTrendChart() {
        trendChart.getData().clear();
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Daily Spending");
        
        // Group by date and sum amounts
        filteredExpenses.stream()
            .collect(Collectors.groupingBy(
                Expense::getDate,
                TreeMap::new, // Maintain chronological order
                Collectors.summingDouble(Expense::getAmount)
            ))
            .forEach((date, amount) -> {
                series.getData().add(new XYChart.Data<>(
                    date.format(DateTimeFormatter.ofPattern("MMM dd")),
                    amount
                ));
            });
        
        trendChart.getData().add(series);
    }

    private void loadExpenses() {
        File file = new File(CSV_FILE);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(CSV_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length >= 6) {
                    LocalDate date = LocalDate.parse(parts[0]);
                    String category = parts[1];
                    double amount = Double.parseDouble(parts[2]);
                    String description = parts[3];
                    String paymentMethod = parts[4];
                    String tags = parts[5];
                    
                    expenses.add(new Expense(
                        date, category, amount, 
                        description, paymentMethod, tags
                    ));
                }
            }
            filteredExpenses.setAll(expenses);
        } catch (IOException e) {
            showAlert("Error", "Failed to load expenses: " + e.getMessage());
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
            updateDataFile();
            showAlert("Success", "Data exported to CSV successfully");
        }
    }

    private void exportPdfReport() {
        // In a real implementation, you would use a PDF library like iText
        showAlert("Info", "PDF export would be implemented here");
    }

    private boolean showLoginDialog() {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Budgify Login");
        dialog.setHeaderText("Enter your credentials");

        // Set the button types
        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // Create the username and password fields
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

        // Convert the result to a username-password-pair
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(username.getText(), password.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();

        if (result.isPresent()) {
            // Simple validation - in real app you'd check against a database
            return "admin".equals(result.get().getKey()) && 
                   "budgify".equals(result.get().getValue());
        }
        return false;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}