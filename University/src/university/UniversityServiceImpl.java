package university;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.net.Socket;

public class UniversityServiceImpl extends UnicastRemoteObject implements UniversityService {

    protected UniversityServiceImpl() throws RemoteException {
        super();
    }

    // Helper method to send data over a raw socket connection
    private void sendSocketLog(String message) {
        try (Socket socket = new Socket("localhost", 8888);
             java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true)) {
            out.println(message);
        } catch (Exception e) {
            System.out.println("[SOCKET CLIENT WARNING] Could not send log to Socket Server: " + e.getMessage());
        }
    }

    @Override
    public void addStudent(Student s) throws RemoteException {
        try {
            Connection con = DatabaseConnection.connect();
            String sql = "INSERT INTO students VALUES (?, ?, ?, ?, ?)";
            PreparedStatement ps = con.prepareStatement(sql);

            ps.setInt(1, s.getId());
            ps.setString(2, s.getName());
            ps.setString(3, s.getDepartment());
            ps.setInt(4, s.getYear());
            ps.setString(5, s.getSection());

            ps.executeUpdate();
            System.out.println("Student Added Through RMI");

            // SOCKET SEND
            sendSocketLog("New Student Added via RMI -> ID: " + s.getId() + ", Name: " + s.getName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Student> getStudents() throws RemoteException {
        List<Student> students = new ArrayList<>();
        try {
            Connection con = DatabaseConnection.connect();
            String sql = "SELECT * FROM students";
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Student s = new Student(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("department"),
                        rs.getInt("year"),
                        rs.getString("section"));
                students.add(s);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return students;
    }

    @Override
    public void addTeacher(Teacher t) throws RemoteException {
        try {
            Connection con = DatabaseConnection.connect();
            String sql = "INSERT INTO teachers VALUES (?, ?, ?)"; 
            PreparedStatement ps = con.prepareStatement(sql);
            
            ps.setInt(1, t.getId());
            ps.setString(2, t.getName());
            ps.setString(3, t.getDepartment());

            ps.executeUpdate();
            System.out.println("Teacher Added Through RMI");
            
            // SOCKET SEND
            sendSocketLog("New Teacher Added via RMI -> ID: " + t.getId() + ", Name: " + t.getName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Teacher> getTeachers() throws RemoteException {
        List<Teacher> teachers = new ArrayList<>();
        try {
            Connection con = DatabaseConnection.connect();
            String sql = "SELECT * FROM teachers";
            PreparedStatement ps = con.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Teacher t = new Teacher(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("department"));
                teachers.add(t);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        return teachers;
    }
}