package university;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIServer {

    public static void main(String[] args) {

        try {

            UniversityService service = new UniversityServiceImpl();

            Registry registry = LocateRegistry.createRegistry(1099);

            registry.rebind("UniversityService", service);

            System.out.println("RMI Server Running...");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}