package university;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface UniversityService extends Remote {

    void addStudent(Student s) throws RemoteException;

    List<Student> getStudents() throws RemoteException;

    void addTeacher(Teacher t) throws RemoteException;

    List<Teacher> getTeachers() throws RemoteException;
}