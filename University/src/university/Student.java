package university;

import java.io.Serializable;

public class Student implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private int id;
    private String name;
    private String department;
    private int year;
    private String section;

    public Student(int id, String name, String department, int year, String section) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.year = year;
        this.section = section;
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

    public int getYear() {
        return year;
    }

    public String getSection() {
        return section;
    }
}