package university;

import java.sql.Connection;
import java.sql.DriverManager;

public class DatabaseConnection {

    public static Connection connect() {

        try {

            Class.forName("com.mysql.cj.jdbc.Driver");

            Connection con = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/school",
                    "root",
                    ""
            );

            System.out.println("CONNECTED!");
            return con;

        } catch (Exception e) {

            System.out.println("DATABASE ERROR:");
            e.printStackTrace();

            return null;
        }
    }
}