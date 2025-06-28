// Budgify.java - Enhanced JavaFX Expense Tracker (Java 17+)
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.chart.*;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.DatePicker;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;
import java.io.FileWriter;
import java.io.FileReader;
import java.time.LocalDate;
import javafx.scene.image.ImageView;
import javafx.geometry.Pos;
import javafx.util.Duration;

public class Budgify extends Application {

    private TextField typeField, amountField;
    private DatePicker datePicker;
    private TableView<Expense> expenseTable;
    private final String CSV_FILE = "expenses.csv";
    private ObservableList<Expense> expenses = FXCollections.observableArrayList();

    // Add these as fields:
    private TabPane mainTabPane;
    private Tab categoryTab;
    private Tab monthlyTab;
    private ComboBox<String> categoryBox;

    public static class Expense {
        private LocalDate date;
        private String category;
        private double amount;
        private String description;
        private String paymentMethod;
        private String tags;

        public Expense(LocalDate date, String category, double amount, String description, String paymentMethod, String tags) {
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
    }

    @Override
    public void start(Stage primaryStage) {
        if (!showLoginDialog()) return;
        primaryStage.setTitle("Budgify - Expense Tracker");

        loadExpenses();

        VBox inputForm = createInputForm();
        expenseTable = createExpenseTable();
        TabPane charts = createCharts();

        VBox leftPane = new VBox(20, inputForm, expenseTable);
        leftPane.setPadding(new Insets(10));
        leftPane.setAlignment(Pos.TOP_CENTER);

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(leftPane, charts);
        splitPane.setDividerPositions(0.45);

        Scene scene = new Scene(splitPane, 1000, 600);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createInputForm() {
        VBox formBox = new VBox(15);
        formBox.setPadding(new Insets(30));
        formBox.setAlignment(Pos.TOP_CENTER);

        HBox dateBox = new HBox(10, new Label("Date:"), datePicker = new DatePicker(LocalDate.now()));

        categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll("Expenses", "Necessities", "Assets", "Income");
        categoryBox.setPromptText("Select Category");
        HBox categoryHBox = new HBox(10, new Label("Category:"), categoryBox);

        HBox amountBox = new HBox(10, new Label("Amount:"), amountField = new TextField());

        TextField descriptionField = new TextField();
        descriptionField.setPromptText("Description");
        HBox descriptionBox = new HBox(10, new Label("Description:"), descriptionField);

        ComboBox<String> paymentBox = new ComboBox<>();
        paymentBox.getItems().addAll("Cash", "Credit Card", "Bank Transfer", "Other");
        paymentBox.setPromptText("Payment Method");
        HBox paymentHBox = new HBox(10, new Label("Payment:"), paymentBox);

        TextField tagsField = new TextField();
        tagsField.setPromptText("Comma-separated tags");
        HBox tagsBox = new HBox(10, new Label("Tags:"), tagsField);

        Button addButton = new Button("Add Entry");
        addButton.setOnAction(e -> saveExpense(
            descriptionField.getText(),
            paymentBox.getValue(),
            tagsField.getText()
        ));
        Button deleteButton = new Button("Delete Selected");
        deleteButton.setOnAction(e -> deleteSelected());
        Button exportButton = new Button("Export CSV");
        exportButton.setOnAction(e -> exportCSV());

        HBox buttonBox = new HBox(15, addButton, deleteButton, exportButton);
        buttonBox.setAlignment(Pos.CENTER);

        formBox.getChildren().addAll(dateBox, categoryHBox, amountBox, descriptionBox, paymentHBox, tagsBox, buttonBox);
        return formBox;
    }

    private TableView<Expense> createExpenseTable() {
        TableView<Expense> table = new TableView<>();
        table.setItems(expenses);

        TableColumn<Expense, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));

        TableColumn<Expense, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));

        TableColumn<Expense, Double> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));

        TableColumn<Expense, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));

        TableColumn<Expense, String> paymentCol = new TableColumn<>("Payment");
        paymentCol.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));

        TableColumn<Expense, String> tagsCol = new TableColumn<>("Tags");
        tagsCol.setCellValueFactory(new PropertyValueFactory<>("tags"));

        table.getColumns().addAll(dateCol, categoryCol, amountCol, descCol, paymentCol, tagsCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        return table;
    }

    private TabPane createCharts() {
        TabPane tabPane = new TabPane();
        
        // Pie chart by category
        PieChart categoryChart = new PieChart();
        updatePieChart(categoryChart);
        categoryTab = new Tab("By Category", categoryChart);
        
        // Bar chart by month
        BarChart<String, Number> monthlyChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        updateBarChart(monthlyChart);
        monthlyTab = new Tab("Monthly Overview", monthlyChart);

        tabPane.getTabs().addAll(categoryTab, monthlyTab);
        
        Tab imageTab = new Tab("Python Analysis");
        VBox imageBox = new VBox(10);
        imageBox.setPadding(new Insets(10));
        imageBox.setAlignment(Pos.CENTER);
        ImageView categoryImg = new ImageView("file:category_chart.png");
        ImageView monthlyImg = new ImageView("file:monthly_chart.png");
        categoryImg.setFitWidth(350); categoryImg.setPreserveRatio(true);
        monthlyImg.setFitWidth(350); monthlyImg.setPreserveRatio(true);
        imageBox.getChildren().addAll(new Label("Category Pie Chart:"), categoryImg, new Label("Monthly Bar Chart:"), monthlyImg);
        imageTab.setContent(imageBox);
        tabPane.getTabs().add(imageTab);
        
        return tabPane;
    }

    private void updatePieChart(PieChart chart) {
        chart.getData().clear();
        expenses.stream()
            .collect(Collectors.groupingBy(Expense::getCategory,
                     Collectors.summingDouble(Expense::getAmount)))
            .forEach((category, total) ->
                chart.getData().add(new PieChart.Data(category + " ($" + String.format("%.2f", total) + ")", total)));
    }

    private void updateBarChart(BarChart<String, Number> chart) {
        chart.getData().clear();
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Monthly Expenses");
        
        // Group expenses by month
        expenses.stream()
            .collect(Collectors.groupingBy(e -> e.getDate().getMonth().toString(), 
                     Collectors.summingDouble(Expense::getAmount)))
            .forEach((month, total) -> 
                series.getData().add(new XYChart.Data<>(month, total)));
        
        chart.getData().add(series);
    }

    private void saveExpense(String description, String paymentMethod, String tags) {
        LocalDate date = datePicker.getValue();
        String category = categoryBox.getValue();
        String amountText = amountField.getText().trim();

        if (category == null || amountText.isEmpty()) {
            showAlert("Error", "Please fill in all fields.");
            return;
        }

        try {
            double amount = Double.parseDouble(amountText);
            Expense newExpense = new Expense(date, category, amount, description, paymentMethod, tags);
            expenses.add(newExpense);

            // Save to file (append)
            try (FileWriter writer = new FileWriter(CSV_FILE, true)) {
                writer.write(String.format("%s,%s,%.2f,%s,%s,%s\n", date, category, amount, description, paymentMethod, tags));
            }

            // Clear fields
            categoryBox.getSelectionModel().clearSelection();
            amountField.clear();

            // Update charts
            updateCharts();

            // Animation
            FadeTransition ft = new FadeTransition(Duration.millis(400), expenseTable);
            ft.setFromValue(0.5);
            ft.setToValue(1.0);
            ft.play();

        } catch (NumberFormatException e) {
            showAlert("Error", "Please enter a valid amount.");
        } catch (IOException e) {
            showAlert("Error", "Failed to save entry to file.");
        }
    }

    private void deleteSelected() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            expenses.remove(selected);
            exportCSV(); // Save all current expenses after deletion
            updateCharts();
        } else {
            showAlert("Error", "Please select an expense to delete.");
        }
    }

    private void exportCSV() {
        try (FileWriter writer = new FileWriter(CSV_FILE)) {
            for (Expense e : expenses) {
                writer.write(String.format("%s,%s,%.2f,%s,%s,%s\n",
                    e.getDate(), e.getCategory(), e.getAmount(),
                    e.getDescription(), e.getPaymentMethod(), e.getTags()));
            }
            showAlert("Export", "CSV exported successfully!");
        } catch (IOException e) {
            showAlert("Error", "Failed to export CSV.");
        }
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
                    expenses.add(new Expense(date, category, amount, description, paymentMethod, tags));
                }
            }
        } catch (IOException e) {
            showAlert("Error", "Failed to load expenses from file.");
        }
    }

    private void updateCharts() {
        if (categoryTab.getContent() instanceof PieChart) {
            updatePieChart((PieChart) categoryTab.getContent());
        }
        if (monthlyTab.getContent() instanceof BarChart) {
            updateBarChart((BarChart<String, Number>) monthlyTab.getContent());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Simple login dialog (for demo)
    private boolean showLoginDialog() {
        while (true) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Login");
            dialog.setHeaderText("Enter username (any) and password (budgify):");
            dialog.setContentText("Username:");
            String username = dialog.showAndWait().orElse("");
            if (username.isEmpty()) return false;

            TextInputDialog pwdDialog = new TextInputDialog();
            pwdDialog.setTitle("Password");
            pwdDialog.setHeaderText("Enter password:");
            pwdDialog.setContentText("Password:");
            String password = pwdDialog.showAndWait().orElse("");
            if ("budgify".equals(password)) {
                return true;
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Incorrect password. Try again.");
                alert.showAndWait();
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

