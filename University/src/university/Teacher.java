package university;

import java.io.Serializable;

public class Teacher implements Serializable {

    private static final long serialVersionUID = 1L;
    private int id;
    private String name;
    private String department;

    public Teacher(int id, String name, String department) {
        this.id = id;
        this.name = name;
        this.department = department;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDepartment() {
        return department;
    }
}