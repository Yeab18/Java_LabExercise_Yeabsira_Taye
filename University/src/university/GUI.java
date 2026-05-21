package university;

import javafx.application.Application;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

public class GUI extends Application {

    TableView<Student> output = new TableView<>();
    TableView<Teacher> teacherOutput = new TableView<>();

    @Override
    public void start(Stage stage) {

        // ================= RMI CONNECTION =================

        UniversityService service = null;

        try {
            // Locates the RMI registry running on localhost at port 1099
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);

            // Looks up the service using the precise registry name matching RMIServer
            service = (UniversityService) registry.lookup("UniversityService");

            System.out.println("Connected To RMI Server");

        } catch (Exception e) {
            e.printStackTrace();
            showAlertDialog(Alert.AlertType.ERROR, "Connection Error",
                    "Could not connect to RMI Server.\nMake sure RMIServer is running!");
        }

        UniversityService finalService = service;

        // ================= STUDENT FIELDS =================

        TextField sid = new TextField();
        sid.setPromptText("Student ID");

        TextField sname = new TextField();
        sname.setPromptText("Student Name");

        TextField sdept = new TextField();
        sdept.setPromptText("Department");

        TextField syear = new TextField();
        syear.setPromptText("Year");

        TextField ssection = new TextField();
        ssection.setPromptText("Section");

        Button addStudent = new Button("Add Student");
        Button showStudents = new Button("Show Students");

        // ================= STUDENT TABLE =================

        TableColumn<Student, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getId()).asObject());

        TableColumn<Student, String> nameCol = new TableColumn<>("NAME");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));

        TableColumn<Student, String> deptCol = new TableColumn<>("DEPARTMENT");
        deptCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDepartment()));

        TableColumn<Student, Integer> yearCol = new TableColumn<>("YEAR");
        yearCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getYear()).asObject());

        TableColumn<Student, String> sectionCol = new TableColumn<>("SECTION");
        sectionCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSection()));

        output.getColumns().clear();
        output.getColumns().addAll(idCol, nameCol, deptCol, yearCol, sectionCol);

        // ================= ADD STUDENT =================

        addStudent.setOnAction(e -> {
            if (finalService == null) {
                showAlertDialog(Alert.AlertType.ERROR, "Connection Error", "Not connected to RMI server.");
                return;
            }
            try {
                Student s = new Student(
                        Integer.parseInt(sid.getText()),
                        sname.getText(),
                        sdept.getText(),
                        Integer.parseInt(syear.getText()),
                        ssection.getText());

                finalService.addStudent(s);

                sid.clear();
                sname.clear();
                sdept.clear();
                syear.clear();
                ssection.clear();

                System.out.println("Student Added");
                showAlertDialog(Alert.AlertType.INFORMATION, "Success", "Student Added Successfully!");

            } catch (NumberFormatException nfe) {
                showAlertDialog(Alert.AlertType.WARNING, "Input Error", "Please enter valid numbers for ID and Year.");
            } catch (Exception ex) {
                ex.printStackTrace();
                showAlertDialog(Alert.AlertType.ERROR, "Server Error", "Failed to add student:\n" + ex.getMessage());
            }
        });

        // ================= SHOW STUDENTS =================

        showStudents.setOnAction(e -> {
            if (finalService == null) {
                showAlertDialog(Alert.AlertType.ERROR, "Connection Error", "Not connected to RMI server.");
                return;
            }
            try {
                output.getItems().clear();

                List<Student> students = finalService.getStudents();

                if (students == null || students.isEmpty()) {
                    showAlertDialog(Alert.AlertType.INFORMATION, "Database Empty",
                            "No students found in the database.");
                } else {
                    output.getItems().addAll(students);
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                showAlertDialog(Alert.AlertType.ERROR, "RMI Error", "Failed to fetch students:\n" + ex.toString());
            }
        });

        // ================= TEACHER FIELDS =================

        TextField tid = new TextField();
        tid.setPromptText("Teacher ID");

        TextField tname = new TextField();
        tname.setPromptText("Teacher Name");

        TextField tdept = new TextField();
        tdept.setPromptText("Department");

        Button addTeacher = new Button("Add Teacher");
        Button showTeachers = new Button("Show Teachers");

        // ================= TEACHER TABLE =================

        TableColumn<Teacher, Integer> tidCol = new TableColumn<>("ID");
        tidCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getId()).asObject());

        TableColumn<Teacher, String> tnameCol = new TableColumn<>("NAME");
        tnameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));

        TableColumn<Teacher, String> tdeptCol = new TableColumn<>("DEPARTMENT");
        tdeptCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDepartment()));

        teacherOutput.getColumns().clear();
        teacherOutput.getColumns().addAll(tidCol, tnameCol, tdeptCol);

        // ================= ADD TEACHER =================

        addTeacher.setOnAction(e -> {
            if (finalService == null) {
                showAlertDialog(Alert.AlertType.ERROR, "Connection Error", "Not connected to RMI server.");
                return;
            }
            try {
                Teacher t = new Teacher(
                        Integer.parseInt(tid.getText()),
                        tname.getText(),
                        tdept.getText());

                finalService.addTeacher(t);

                tid.clear();
                tname.clear();
                tdept.clear();

                System.out.println("Teacher Added");
                showAlertDialog(Alert.AlertType.INFORMATION, "Success", "Teacher Added Successfully!");

            } catch (NumberFormatException nfe) {
                showAlertDialog(Alert.AlertType.WARNING, "Input Error", "Please enter a valid number for Teacher ID.");
            } catch (Exception ex) {
                ex.printStackTrace();
                showAlertDialog(Alert.AlertType.ERROR, "Server Error", "Failed to add teacher:\n" + ex.getMessage());
            }
        });

        // ================= SHOW TEACHERS =================

        showTeachers.setOnAction(e -> {
            if (finalService == null) {
                showAlertDialog(Alert.AlertType.ERROR, "Connection Error", "Not connected to RMI server.");
                return;
            }
            try {
                teacherOutput.getItems().clear();

                List<Teacher> teachers = finalService.getTeachers();

                if (teachers == null || teachers.isEmpty()) {
                    showAlertDialog(Alert.AlertType.INFORMATION, "Database Empty",
                            "No teachers found in the database.");
                } else {
                    teacherOutput.getItems().addAll(teachers);
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                showAlertDialog(Alert.AlertType.ERROR, "RMI Error", "Failed to fetch teachers:\n" + ex.toString());
            }
        });

        // ================= LAYOUT =================

        VBox root = new VBox(10,
                new Label("Students"),
                sid, sname, sdept, syear, ssection,
                addStudent, showStudents, output,
                new Separator(),
                new Label("Teachers"),
                tid, tname, tdept,
                addTeacher, showTeachers, teacherOutput);

        root.setPadding(new Insets(15));
        Scene scene = new Scene(root, 700, 700);

        stage.setScene(scene);
        stage.setTitle("University System");
        stage.show();
    }

    // Helper method to handle standard JavaFX alert popups safely
    private void showAlertDialog(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch();
    }
}