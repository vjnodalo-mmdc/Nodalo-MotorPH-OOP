package com.mycompany.oop_nodalo;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

public class OOP_Nodalo {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginScreen().show());
    }
}

class LoginScreen {
    public void show() {
        JFrame frame = new JFrame("Login");
        frame.setSize(350, 220);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridLayout(5, 1));

        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();

        frame.add(new JLabel("Username:"));
        frame.add(usernameField);
        frame.add(new JLabel("Password:"));
        frame.add(passwordField);

        JPanel btnPanel = new JPanel();
        JButton loginBtn = new JButton("Login");
        loginBtn.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (CredentialManager.authenticate(username, password)) {
                AuditLogger.log("Login Success", username);
                frame.dispose();
                new PayrollGUI(username).createAndShowGUI(); // pass user for logging
            } else {
                AuditLogger.log("Login Failed", username);
                JOptionPane.showMessageDialog(frame, "Invalid username or password.", "Login Failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnPanel.add(loginBtn);
        frame.add(btnPanel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

class PayrollGUI {
    private JFrame frame;
    private JTextField employeeIdField;
    private JComboBox<String> monthBox;
    private JTextArea outputArea;
    private final String currentUser;

    public PayrollGUI(String username) {
        this.currentUser = username;
    }

    public void createAndShowGUI() {
        frame = new JFrame("MotorPH Employee Payroll");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 500);

        JPanel inputPanel = new JPanel(new GridLayout(3, 4, 8, 8));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        inputPanel.add(new JLabel("Employee ID:"));
        employeeIdField = new JTextField();
        inputPanel.add(employeeIdField);

        inputPanel.add(new JLabel("Select Month:"));
        String[] months = new String[]{"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        monthBox = new JComboBox<>(months);
        inputPanel.add(monthBox);

        JButton button = new JButton("Generate Payroll");
        button.addActionListener(e -> generatePayroll());
        inputPanel.add(button);

        JButton viewEmployeesBtn = new JButton("View Employees");
        viewEmployeesBtn.addActionListener(e -> new EmployeeListFrame(currentUser).showFrame());
        inputPanel.add(viewEmployeesBtn);

        JButton newEmployeeBtn = new JButton("New Employee");
        newEmployeeBtn.addActionListener(e -> new NewEmployeeForm(currentUser).showForm());
        inputPanel.add(newEmployeeBtn);

        JButton backupBtn = new JButton("Backup & Restore");
        backupBtn.addActionListener(e -> {
            AuditLogger.log("Backup initiated", currentUser);
            BackupManager.backupData();
            AuditLogger.log("Restore initiated", currentUser);
            BackupManager.restoreData();
        });
        inputPanel.add(backupBtn);

        frame.add(inputPanel, BorderLayout.NORTH);

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        frame.add(new JScrollPane(outputArea), BorderLayout.CENTER);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void generatePayroll() {
        String empId = employeeIdField.getText().trim();
        String selectedMonth = (String) monthBox.getSelectedItem();

        if (empId.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Employee ID is required.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Employee employee = CSVHandler.getEmployee(empId);
        if (employee == null) {
            outputArea.setText("Employee not found.\n");
            return;
        }

        List<AttendanceRecord> attendance = CSVHandler.getAttendance(empId, selectedMonth);
        if (attendance.isEmpty()) {
            outputArea.setText("No attendance records found for this month.\n");
            return;
        }

        int monthNum = PayrollProcessor.getMonthNumber(selectedMonth);
        PayrollReport report = PayrollProcessor.generatePayroll(employee, attendance, monthNum);
        outputArea.setText(report.toString());
        AuditLogger.log("Payroll generated for " + empId + " month " + selectedMonth, currentUser);
    }
}

class EmployeeListFrame {
    private JTable table;
    private JTextArea outputArea;
    private JComboBox<String> monthBox;
    private DefaultTableModel model;
    private final String currentUser;

    public EmployeeListFrame(String username) {
        this.currentUser = username;
    }

    public void showFrame() {
        JFrame frame = new JFrame("Employee List & Payroll Computation");
        frame.setSize(900, 650);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        model = new DefaultTableModel(new String[]{"Employee ID", "Last Name", "First Name", "SSS Number", "PhilHealth Number", "TIN", "Pag-IBIG Number"}, 0);
        table = new JTable(model);
        refreshTable();

        JScrollPane tableScrollPane = new JScrollPane(table);

        JPanel controlsPanel = new JPanel();
        controlsPanel.add(new JLabel("Select Month:"));
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        monthBox = new JComboBox<>(months);
        controlsPanel.add(monthBox);

        JButton computeButton = new JButton("Compute Salary");
        computeButton.addActionListener(e -> computeSalary());
        controlsPanel.add(computeButton);

        JButton editButton = new JButton("Edit");
        editButton.addActionListener(e -> editEmployee());
        controlsPanel.add(editButton);

        JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> deleteEmployee());
        controlsPanel.add(deleteButton);

        outputArea = new JTextArea(10, 70);
        outputArea.setEditable(false);
        JScrollPane outputScrollPane = new JScrollPane(outputArea);

        frame.add(tableScrollPane, BorderLayout.NORTH);
        frame.add(controlsPanel, BorderLayout.CENTER);
        frame.add(outputScrollPane, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        RefreshManager.register(() -> refreshTable());
    }

    private void editEmployee() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Please select an employee to edit.");
            return;
        }

        String employeeId = table.getValueAt(selectedRow, 0).toString();
        Employee employee = CSVHandler.getEmployee(employeeId);
        if (employee == null) {
            JOptionPane.showMessageDialog(null, "Employee not found.");
            return;
        }

        new NewEmployeeForm(currentUser).showForm(employee); // overloaded method
        AuditLogger.log("Edit initiated for " + employeeId, currentUser);
    }

    private void deleteEmployee() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Please select an employee to delete.");
            return;
        }

        String employeeId = table.getValueAt(selectedRow, 0).toString();
        int confirm = JOptionPane.showConfirmDialog(null, "Are you sure you want to delete employee ID " + employeeId + "?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            CSVHandler.deleteEmployee(employeeId);
            JOptionPane.showMessageDialog(null, "Employee deleted.");
            AuditLogger.log("Deleted employee " + employeeId, currentUser);
            refreshTable();
        }
    }

    private void refreshTable() {
        model.setRowCount(0);
        for (Employee emp : CSVHandler.getAllEmployees()) {
            model.addRow(new Object[]{
                    emp.getId(),
                    emp.getLastName(),
                    emp.getFirstName(),
                    emp.getSssNumber(),
                    emp.getPhilHealthNumber(),
                    emp.getTin(),
                    emp.getPagIbigNumber()
            });
        }
    }

    private void computeSalary() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Please select an employee from the table.");
            return;
        }

        String employeeId = table.getValueAt(selectedRow, 0).toString();
        String selectedMonth = (String) monthBox.getSelectedItem();

        Employee employee = CSVHandler.getEmployee(employeeId);
        if (employee == null) {
            outputArea.setText("Selected employee not found.");
            return;
        }

        List<AttendanceRecord> attendance = CSVHandler.getAttendance(employeeId, selectedMonth);
        if (attendance.isEmpty()) {
            outputArea.setText("No attendance data found for selected month.");
            return;
        }

        int monthNum = PayrollProcessor.getMonthNumber(selectedMonth);
        PayrollReport report = PayrollProcessor.generatePayroll(employee, attendance, monthNum);
        outputArea.setText(report.toString());
        AuditLogger.log("Payroll computed for " + employeeId + " month " + selectedMonth, currentUser);
    }
}

class NewEmployeeForm {
    private final String currentUser;

    public NewEmployeeForm(String username) {
        this.currentUser = username;
    }

    public void showForm() {
        JFrame frame = new JFrame("Add New Employee");
        frame.setSize(420, 520);
        frame.setLayout(new GridLayout(14, 2));

        JTextField[] fields = new JTextField[13];
        String[] labels = {"ID", "Last Name", "First Name", "Department", "Position", "SSS", "PhilHealth", "TIN", "Pag-IBIG", "Rate Per Day", "Rice Subsidy", "Phone Allowance", "Clothing Allowance"};

        for (int i = 0; i < labels.length; i++) {
            frame.add(new JLabel(labels[i]));
            fields[i] = new JTextField();
            frame.add(fields[i]);
        }

        JButton submit = new JButton("Submit");
        submit.addActionListener(e -> {
            try {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < fields.length; i++) {
                    if (fields[i].getText().trim().isEmpty()) {
                        JOptionPane.showMessageDialog(frame, labels[i] + " is required.");
                        return;
                    }
                    sb.append(fields[i].getText().trim());
                    if (i < fields.length - 1) sb.append(",");
                }

                FileWriter fw = new FileWriter("src/main/resources/employees.csv", true);
                fw.write(sb.toString() + "\n");
                fw.close();

                JOptionPane.showMessageDialog(frame, "Employee added successfully!");
                AuditLogger.log("Added employee " + fields[0].getText().trim(), currentUser);
                frame.dispose();
                RefreshManager.trigger(); // Refresh JTable
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Error saving employee.");
            }
        });

        frame.add(submit);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void showForm(Employee existingEmployee) {
        JFrame frame = new JFrame("Edit Employee");
        frame.setSize(420, 520);
        frame.setLayout(new GridLayout(14, 2));

        JTextField[] fields = new JTextField[13];
        String[] labels = {"ID", "Last Name", "First Name", "Department", "Position", "SSS", "PhilHealth", "TIN", "Pag-IBIG", "Rate Per Day", "Rice Subsidy", "Phone Allowance", "Clothing Allowance"};

        for (int i = 0; i < labels.length; i++) {
            frame.add(new JLabel(labels[i]));
            fields[i] = new JTextField();
            frame.add(fields[i]);
        }

        // Pre-fill
        fields[0].setText(existingEmployee.getId());
        fields[1].setText(existingEmployee.getLastName());
        fields[2].setText(existingEmployee.getFirstName());
        fields[3].setText(existingEmployee.getDepartment());
        fields[4].setText(existingEmployee.getPosition());
        fields[5].setText(existingEmployee.getSssNumber());
        fields[6].setText(existingEmployee.getPhilHealthNumber());
        fields[7].setText(existingEmployee.getTin());
        fields[8].setText(existingEmployee.getPagIbigNumber());
        fields[9].setText(String.valueOf(existingEmployee.getRatePerDay()));
        fields[10].setText(String.valueOf(existingEmployee.getRiceSubsidy()));
        fields[11].setText(String.valueOf(existingEmployee.getPhoneAllowance()));
        fields[12].setText(String.valueOf(existingEmployee.getClothingAllowance()));

        fields[0].setEditable(false); // Don't allow changing ID

        JButton submit = new JButton("Update");
        submit.addActionListener(e -> {
            try {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < fields.length; i++) {
                    if (fields[i].getText().trim().isEmpty()) {
                        JOptionPane.showMessageDialog(frame, labels[i] + " is required.");
                        return;
                    }
                    sb.append(fields[i].getText().trim());
                    if (i < fields.length - 1) sb.append(",");
                }

                CSVHandler.updateEmployee(existingEmployee.getId(), sb.toString());
                JOptionPane.showMessageDialog(frame, "Employee updated successfully!");
                AuditLogger.log("Updated employee " + existingEmployee.getId(), currentUser);
                frame.dispose();
                RefreshManager.trigger(); // refresh table
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Error updating employee.");
            }
        });

        frame.add(submit);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

class RefreshManager {
    private static Runnable listener;

    public static void register(Runnable l) {
        listener = l;
    }

    public static void trigger() {
        if (listener != null) listener.run();
    }
}

class CredentialManager {
    public static boolean authenticate(String username, String password) {
        try (InputStream is = CredentialManager.class.getClassLoader().getResourceAsStream("credentials.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            if (is == null) return false;
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(",");
                if (parts.length == 2) {
                    if (parts[0].equals(username) && parts[1].equals(password)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}

class CSVHandler {
    public static List<Employee> getAllEmployees() {
        System.out.println("getAllEmployees() called");
    List<Employee> employees = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                CSVHandler.class.getResourceAsStream("/employees.csv")))) {
        System.out.println("Reading CSV...");

        String line;
        reader.readLine(); // skip header

        while ((line = reader.readLine()) != null) {
            System.out.println("Line: " + line);
            String[] data = line.split(",");

            String department = data[3].trim();
            String position = data[4].trim();

            Employee emp;
            

                if (department.equalsIgnoreCase("Finance")) {
                    emp = new FinanceEmployee(
                    data[0], data[1], data[2], data[3], data[4],
                    Double.parseDouble(data[9]), Double.parseDouble(data[10]), Double.parseDouble(data[11]),
                    Double.parseDouble(data[12]),
                    data[5],
                    data[6],
                    data[7],
                    data[8]
                );

                } else if (department.equalsIgnoreCase("IT")) {
                    emp = new ITEmployee(
                        data[0], data[1], data[2], data[3], data[4],
                    Double.parseDouble(data[9]), Double.parseDouble(data[10]), Double.parseDouble(data[11]),
                    Double.parseDouble(data[12]),
                    data[5],
                    data[6],
                    data[7],
                    data[8]
                    );

                } else if (position.equalsIgnoreCase("Probationary")) {
                    emp = new ProbationaryEmployee(
                        data[0], data[1], data[2], data[3], data[4],
                    Double.parseDouble(data[9]), Double.parseDouble(data[10]), Double.parseDouble(data[11]),
                    Double.parseDouble(data[12]),
                    data[5],
                    data[6],
                    data[7],
                    data[8]
                    );

                } else {
                    emp = new RegularEmployee(
                        data[0], data[1], data[2], data[3], data[4],
                    Double.parseDouble(data[9]), Double.parseDouble(data[10]), Double.parseDouble(data[11]),
                    Double.parseDouble(data[12]),
                    data[5],
                    data[6],
                    data[7],
                    data[8]
                    );
                }
                
            System.out.println("Created employee: " + emp.getId());

            employees.add(emp);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
    System.out.println("Total employees: " + employees.size());
    return employees;
    }
    public static Employee getEmployee(String employeeID) {
        try (BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/employees.csv"))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.replaceAll("\"", "").split(",");
                if (data[0].trim().equalsIgnoreCase(employeeID.trim())) {
                    String department = data[3];
                    String position = data[4];

                    if (department.equalsIgnoreCase("Finance")) {
                        return new FinanceEmployee(data[0], data[1], data[2], data[3], data[4],
                        Double.parseDouble(data[9]),
                        Double.parseDouble(data[10]),
                        Double.parseDouble(data[11]),
                        Double.parseDouble(data[12]),
                        data[5],
                        data[6],
                        data[7],
                        data[8]
                    );

                    } else if (department.equalsIgnoreCase("HR")) {
                        return new HREmployee(data[0], data[1], data[2], data[3], data[4],
                        Double.parseDouble(data[9]),
                        Double.parseDouble(data[10]),
                        Double.parseDouble(data[11]),
                        Double.parseDouble(data[12]),
                        data[5],
                        data[6],
                        data[7],
                        data[8]
                    );

                    } else if (department.equalsIgnoreCase("IT")) {
                        return new ITEmployee(data[0], data[1], data[2], data[3], data[4],
                        Double.parseDouble(data[9]),
                        Double.parseDouble(data[10]),
                        Double.parseDouble(data[11]),
                        Double.parseDouble(data[12]),
                        data[5],
                        data[6],
                        data[7],
                        data[8]
                    );

                    } else if (position.equalsIgnoreCase("Probationary")) {
                        return new ProbationaryEmployee(data[0], data[1], data[2], data[3], data[4],
                        Double.parseDouble(data[9]),
                        Double.parseDouble(data[10]),
                        Double.parseDouble(data[11]),
                        Double.parseDouble(data[12]),
                        data[5],
                        data[6],
                        data[7],
                        data[8]
                    );

                    } else {
                        return new RegularEmployee(data[0], data[1], data[2], data[3], data[4],
                        Double.parseDouble(data[9]),
                        Double.parseDouble(data[10]),
                        Double.parseDouble(data[11]),
                        Double.parseDouble(data[12]),
                        data[5],
                        data[6],
                        data[7],
                        data[8]
                    );
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<AttendanceRecord> getAttendance(String employeeID, String monthName) {
        List<AttendanceRecord> records = new ArrayList<>();
        int month = PayrollProcessor.getMonthNumber(monthName);

        try (InputStream is = CSVHandler.class.getClassLoader().getResourceAsStream("attendance.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            if (is == null) return records;
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                if (data[0].trim().equalsIgnoreCase(employeeID.trim())) {
                    int recordMonth = Integer.parseInt(data[3].split("/")[0]);
                    if (recordMonth == month) {
                        records.add(new AttendanceRecord(data[0], data[3], data[4], data[5]));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return records;
    }

    public static void deleteEmployee(String employeeID) {
        try {
            File inputFile = new File("src/main/resources/employees.csv");
            File tempFile = new File("src/main/resources/employees_temp.csv");

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(employeeID + ",")) {
                    writer.write(line + "\n");
                }
            }

            reader.close();
            writer.close();

            if (!inputFile.delete()) System.err.println("Could not delete original employees.csv");
            if (!tempFile.renameTo(inputFile)) System.err.println("Could not rename temp file to employees.csv");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateEmployee(String employeeID, String updatedLine) {
        try {
            File inputFile = new File("src/main/resources/employees.csv");
            File tempFile = new File("src/main/resources/employees_temp.csv");

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(employeeID + ",")) {
                    writer.write(updatedLine + "\n");
                } else {
                    writer.write(line + "\n");
                }
            }

            reader.close();
            writer.close();

            if (!inputFile.delete()) System.err.println("Could not delete original employees.csv");
            if (!tempFile.renameTo(inputFile)) System.err.println("Could not rename temp file to employees.csv");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

abstract class Employee {
    private String id, lastName, firstName, department, position;
    private double ratePerDay, riceSubsidy, phoneAllowance, clothingAllowance;
    private String sssNumber, philHealthNumber, tin, pagIbigNumber;

    public Employee(String id, String lastName, String firstName, String department, String position,
                    double ratePerDay, double riceSubsidy, double phoneAllowance, double clothingAllowance,
                    String sssNumber, String philHealthNumber, String tin, String pagIbigNumber) {
        this.id = id;
        this.lastName = lastName;
        this.firstName = firstName;
        this.department = department;
        this.position = position;
        this.ratePerDay = ratePerDay;
        this.riceSubsidy = riceSubsidy;
        this.phoneAllowance = phoneAllowance;
        this.clothingAllowance = clothingAllowance;
        this.sssNumber = sssNumber;
        this.philHealthNumber = philHealthNumber;
        this.tin = tin;
        this.pagIbigNumber = pagIbigNumber;
    }

    public abstract double calculateSalary(double hoursWorked);

    public String getId() { return id; }
    public String getLastName() { return lastName; }
    public String getFirstName() { return firstName; }
    public String getDepartment() { return department; }
    public String getPosition() { return position; }
    public double getRatePerDay() { return ratePerDay; }
    public double getRiceSubsidy() { return riceSubsidy; }
    public double getPhoneAllowance() { return phoneAllowance; }
    public double getClothingAllowance() { return clothingAllowance; }
    public String getSssNumber() { return sssNumber; }
    public String getPhilHealthNumber() { return philHealthNumber; }
    public String getTin() { return tin; }
    public String getPagIbigNumber() { return pagIbigNumber; }

    public double getHourlyRate() { return ratePerDay / 8.0; }
}

class RegularEmployee extends Employee {
    public RegularEmployee(String id, String lastName, String firstName, String department, String position,
                           double ratePerDay, double riceSubsidy, double phoneAllowance, double clothingAllowance,
                           String sssNumber, String philHealthNumber, String tin, String pagIbigNumber) {
        super(id, lastName, firstName, department, position,
              ratePerDay, riceSubsidy, phoneAllowance, clothingAllowance,
              sssNumber, philHealthNumber, tin, pagIbigNumber);
    }

    @Override
    public double calculateSalary(double hoursWorked) {
        return getHourlyRate() * hoursWorked;
    }
}

class ProbationaryEmployee extends Employee {
    public ProbationaryEmployee(String id, String lastName, String firstName, String department, String position,
                                double ratePerDay, double riceSubsidy, double phoneAllowance, double clothingAllowance,
                                String sssNumber, String philHealthNumber, String tin, String pagIbigNumber) {
        super(id, lastName, firstName, department, position,
              ratePerDay, riceSubsidy, phoneAllowance, clothingAllowance,
              sssNumber, philHealthNumber, tin, pagIbigNumber);
    }

    @Override
    public double calculateSalary(double hoursWorked) {
        return getHourlyRate() * hoursWorked * 0.8; // reduced
    }
}

class FinanceEmployee extends Employee {
    public FinanceEmployee(String id, String lastName, String firstName, String department, String position,
                           double ratePerDay, double riceSubsidy, double phoneAllowance, double clothingAllowance,
                           String sssNumber, String philHealthNumber, String tin, String pagIbigNumber) {
        super(id, lastName, firstName, department, position,
              ratePerDay, riceSubsidy, phoneAllowance, clothingAllowance,
              sssNumber, philHealthNumber, tin, pagIbigNumber);
    }

    @Override
    public double calculateSalary(double hoursWorked) {
        return getHourlyRate() * hoursWorked + 2000;
    }
}

class HREmployee extends Employee {
    public HREmployee(String id, String lastName, String firstName, String department, String position,
                      double ratePerDay, double riceSubsidy, double phoneAllowance, double clothingAllowance,
                      String sssNumber, String philHealthNumber, String tin, String pagIbigNumber) {
        super(id, lastName, firstName, department, position,
              ratePerDay, riceSubsidy, phoneAllowance, clothingAllowance,
              sssNumber, philHealthNumber, tin, pagIbigNumber);
    }

    @Override
    public double calculateSalary(double hoursWorked) {
        return getHourlyRate() * hoursWorked + 1500;
    }
}

class ITEmployee extends Employee {
    public ITEmployee(String id, String lastName, String firstName, String department, String position,
                      double ratePerDay, double riceSubsidy, double phoneAllowance, double clothingAllowance,
                      String sssNumber, String philHealthNumber, String tin, String pagIbigNumber) {
        super(id, lastName, firstName, department, position,
              ratePerDay, riceSubsidy, phoneAllowance, clothingAllowance,
              sssNumber, philHealthNumber, tin, pagIbigNumber);
    }

    @Override
    public double calculateSalary(double hoursWorked) {
        return getHourlyRate() * hoursWorked + 3000;
    }
}

class AttendanceRecord {
    private String employeeID, date, timeIn, timeOut;
    public AttendanceRecord(String employeeID, String date, String timeIn, String timeOut) {
        this.employeeID = employeeID;
        this.date = date;
        this.timeIn = timeIn;
        this.timeOut = timeOut;
    }

    public int getMonth() {
        try { return Integer.parseInt(date.split("/")[0]); } catch (Exception e) { return -1; }
    }

    public String getTimeIn() { return timeIn; }
    public String getTimeOut() { return timeOut; }
}

class PayrollProcessor {
    public static PayrollReport generatePayroll(Employee employee, List<AttendanceRecord> attendanceRecords, int selectedMonth) {
        double[] weeklyHours = new double[5];
        double[] weeklyOT = new double[5];
        double totalLateDeduction = 0;
        int workdayCount = 0, currentWeek = 0;
        
        double totalHoursWorked = 0;

        for (AttendanceRecord record : attendanceRecords) {
            if (record.getMonth() == selectedMonth) {
                if (workdayCount % 5 == 0 && workdayCount != 0) currentWeek++;
                if (currentWeek >= 5) currentWeek = 4;
                workdayCount++;

                double inTime = convertTimeToHours(record.getTimeIn());
                double outTime = convertTimeToHours(record.getTimeOut());
                double dailyHours = Math.max(0, Math.min(outTime, 17.0) - Math.max(inTime, 8.0) - 1);
                weeklyHours[currentWeek] += dailyHours;
                
                totalHoursWorked += dailyHours;

                if (outTime > 17.0167) weeklyOT[currentWeek] += (outTime - 17.0167);
                if (inTime > 8.1833 && currentWeek < 4) {
                    totalLateDeduction += ((inTime - 8.1833) * 60 / 60.0) * employee.getHourlyRate();
                }
            }
        }

        double totalGrossSalary = employee.calculateSalary(totalHoursWorked);

        double totalOTPay = 0;
        for (int i = 0; i < 4; i++) {
            totalOTPay += weeklyOT[i] * employee.getHourlyRate() * 1.25;
        }

        double totalAllowances = employee.getRiceSubsidy() 
                + employee.getPhoneAllowance() 
                + employee.getClothingAllowance();

        totalGrossSalary += totalOTPay + totalAllowances;

        double sss = calculateSSSContribution(totalGrossSalary);
        double philhealth = totalGrossSalary * 0.035;
        double pagibig = totalGrossSalary * 0.02;
        double tax = totalGrossSalary * 0.05;

        double deductions = sss + philhealth + pagibig + tax + totalLateDeduction;
        double net = totalGrossSalary - deductions;

        return new PayrollReport(employee.getFirstName() + " " + employee.getLastName(), totalGrossSalary, deductions, net);
    }

    private static double convertTimeToHours(String time) {
        try {
            String[] parts = time.split(":");
            return Integer.parseInt(parts[0]) + Integer.parseInt(parts[1]) / 60.0;
        } catch (Exception e) { return 0; }
    }

    private static double calculateSSSContribution(double compensation) {
        double base = 3250, rate = 22.5, minSSS = 135.00, maxSSS = 1125.00;
        int steps = (int) ((compensation - base) / 500);
        return Math.min(maxSSS, Math.max(minSSS, minSSS + steps * rate));
    }

    public static int getMonthNumber(String month) {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        for (int i = 0; i < months.length; i++) {
            if (months[i].equalsIgnoreCase(month)) return i + 1;
        }
        return -1;
    }
}

class PayrollReport {
    private final String employeeName;
    private final double grossSalary, totalDeductions, netSalary;

    public PayrollReport(String employeeName, double grossSalary, double totalDeductions, double netSalary) {
        this.employeeName = employeeName;
        this.grossSalary = grossSalary;
        this.totalDeductions = totalDeductions;
        this.netSalary = netSalary;
    }

    public String toString() {
        return String.format("Payroll Report for %s\nGross Salary: %.2f\nTotal Deductions: %.2f\nNet Salary: %.2f",
                employeeName, grossSalary, totalDeductions, netSalary);
    }
}

class BackupManager {
    private static final String BACKUP_FOLDER = "src/main/resources/backup/";

    public static void backupData() {
        try {
            new File(BACKUP_FOLDER).mkdirs();
            copyFile("src/main/resources/employees.csv", BACKUP_FOLDER + "employees_backup.csv");
            copyFile("src/main/resources/attendance.csv", BACKUP_FOLDER + "attendance_backup.csv");
            JOptionPane.showMessageDialog(null, "Backup completed successfully!");
            AuditLogger.log("Backup completed", "system");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Backup failed: " + e.getMessage());
            AuditLogger.log("Backup failed: " + e.getMessage(), "system");
        }
    }

    public static void restoreData() {
        try {
            copyFile(BACKUP_FOLDER + "employees_backup.csv", "src/main/resources/employees_restored.csv");
            copyFile(BACKUP_FOLDER + "attendance_backup.csv", "src/main/resources/attendance_restored.csv");
            JOptionPane.showMessageDialog(null, "Data restored successfully!");
            AuditLogger.log("Restore completed", "system");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Restore failed: " + e.getMessage());
            AuditLogger.log("Restore failed: " + e.getMessage(), "system");
        }
    }

    private static void copyFile(String source, String dest) throws IOException {
        File s = new File(source);
        if (!s.exists()) throw new FileNotFoundException(source + " not found");
        try (InputStream is = new FileInputStream(s); OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
        }
    }
}

class AuditLogger {
    private static final String LOG_FILE = "src/main/resources/audit_log.txt";

    public static void log(String action, String user) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String entry = timestamp + " | " + user + " | " + action;
            String hash = hashEntry(entry);
            fw.write(entry + " | HASH:" + hash + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String hashEntry(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedHash = digest.digest(text.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : encodedHash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}

class AESEncryptionDemo {
    // WARNING: For production, manage keys securely (not hard-coded). This is for demonstration only.
    private static final String KEY = "0123456789abcdef0123456789abcdef"; // 32-byte key

    public static String encrypt(String data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes("UTF-8"), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(data.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            return "Encryption failed: " + e.getMessage();
        }
    }

    public static String decrypt(String encryptedData) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes("UTF-8"), "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            return new String(cipher.doFinal(decoded), "UTF-8");
        } catch (Exception e) {
            return "Decryption failed: " + e.getMessage();
        }
    }
}
