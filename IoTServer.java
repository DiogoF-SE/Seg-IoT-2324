
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IoTServer {

    private int port;
    private Map<String, String> users; // map of user-id and password
    private Map<String, Set<String>> domains; // map of domain name and associated devices
    private Map<String, Set<String>> domainPermissions; // map of domain name and users with read permissions
    private Map<String, Float> temperatureData; // map of device-id and last temperature value
    private Map<String, byte[]> imageData; // map of device-id and last image data

    public static void main(String[] args) {
        IoTServer server = new IoTServer(args.length > 0 ? Integer.parseInt(args[0]) : 12345);
        server.start();
    }

    public IoTServer(int port) {
        this.port = port;
        this.users = new HashMap<>();
        this.domains = new HashMap<>();
        this.domainPermissions = new HashMap<>();
        this.temperatureData = new HashMap<>();
        this.imageData = new HashMap<>();

        // Create data directory if it doesn't exist
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdir();
        }

        // Load user data from file or create file if it doesn't exist
        File usersFile = new File("data/users.txt");
        if (usersFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    users.put(parts[0], parts[1]);
                    domainPermissions.put(parts[0], new HashSet<>());
                }
            } catch (IOException e) {
                System.err.println("Error loading user data: " + e.getMessage());
            }
        } else {
            try {
                usersFile.createNewFile();
            } catch (IOException e) {
                System.err.println("Error creating users file: " + e.getMessage());
            }
        }

        // Load device data from file or create file if it doesn't exist
        File devicesFile = new File("data/devices.txt");
        if (devicesFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(devicesFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    String deviceId = parts[0];
                    String domain = parts[1];
                    if (!domains.containsKey(domain)) {
                        domains.put(domain, new HashSet<>());
                    }
                    domains.get(domain).add(deviceId);
                    temperatureData.put(deviceId, null);
                    imageData.put(deviceId, null);
                }
            } catch (IOException e) {
                System.err.println("Error loading device data: " + e.getMessage());
            }
        } else {
            try {
                devicesFile.createNewFile();
            } catch (IOException e) {
                System.err.println("Error creating devices file: " + e.getMessage());
            }
        }
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("IoTServer started on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("A new client is connecting!");
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting IoTServer: " + e.getMessage());
        }
    }

    private synchronized boolean authenticate(String userId, String password) {
        String storedPassword = users.get(userId);
        return storedPassword != null && storedPassword.equals(password);
    }

    private synchronized boolean registerUser(String userId, String password) {
        if (users.containsKey(userId)) {
            return false; // user already exists
        }
        users.put(userId, password);
        domainPermissions.put(userId, new HashSet<>());

        // Write user data to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("data/users.txt", true))) {
            writer.write(userId + "," + password);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing user data: " + e.getMessage());
        }

        return true;
    }

    private synchronized boolean registerDevice(String userId, String deviceId, String domain) {
        if (!domains.containsKey(domain)) {
            domains.put(domain, new HashSet<>());
        }
        Set<String> devices = domains.get(domain);
        if (devices.contains(deviceId)) {
            return false; // device already registered in domain
        }
        devices.add(deviceId);
        temperatureData.put(deviceId, null);
        imageData.put(deviceId, null);

        // Write device data to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("data/devices.txt", true))) {
            writer.write(deviceId + "," + domain);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing device data: " + e.getMessage());
        }

        return true;
    }

    private synchronized boolean addDomainPermission(String userId, String domain) {
        if (!domains.containsKey(domain)) {
            return false; // domain does not exist
        }
        Set<String> permissions = domainPermissions.get(domain);
        return permissions.add(userId);
    }

    private synchronized boolean hasDomainPermission(String userId, String domain) {
        if (!domains.containsKey(domain)) {
            return false; // domain does not exist
        }
        Set<String> permissions = domainPermissions.get(domain);
        if (permissions.contains(userId)) {
            return true;
        }

        // Check if user has permission in file
        try (BufferedReader reader = new BufferedReader(new FileReader("data/users.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(userId) && parts.length > 2) {
                    for (int i = 2; i < parts.length; i++) {
                        if (parts[i].equals(domain)) {
                            permissions.add(userId);
                            return true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading user data: " + e.getMessage());
        }

        return false;
    }

    private synchronized boolean registerTemperatureData(String deviceId, float temperature) {
        if (!temperatureData.containsKey(deviceId)) {
            return false; // device not registered
        }
        temperatureData.put(deviceId, temperature);
        return true;
    }

    private synchronized boolean registerImageData(String deviceId, byte[] image) {
        if (!imageData.containsKey(deviceId)) {
            return false; // device not registered
        }
        imageData.put(deviceId, image);
        return true;
    }

    private class ClientHandler extends Thread {

        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());) {

                System.out.println("Client connected: " + socket.getInetAddress().getHostAddress());
                String userId = (String) in.readObject();
                String password = (String) in.readObject();

                if (authenticate(userId, password)) {
                    out.writeObject("OK-USER");
                } else {
                    if (registerUser(userId, password)) {
                        out.writeObject("OK-NEW-USER");
                    } else {
                        out.writeObject("WRONG-PWD");
                    }
                }

                String deviceId = (String) in.readObject();

                while (!registerDevice(userId, deviceId, "default")) {
                    out.writeObject("NOK-DEVID");
                    deviceId = (String) in.readObject();
                }

                out.writeObject("OK-DEVID");

                String programName = (String) in.readObject();
                int programSize = (Integer) in.readObject();

                try (BufferedReader reader = new BufferedReader(new FileReader("teste.txt"))) {
                    String line;
                    String frespond = "NOK-TESTED";
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts[0].equals(programName) && Integer.parseInt(parts[1]) == programSize) {
                            frespond = "OK-TESTED";
                            break;
                        }
                    }
                    out.writeObject(frespond);
                } catch (IOException e) {
                    System.err.println("Error reading" + e.getMessage());
                    out.writeObject("NOK-TESTED");
                }

                while (true) {
                    String command = (String) in.readObject();
                    String[] parts = command.split(" ");
                    String response = "";

                    switch (parts[0]) {
                        case "CREATE":
                            if (parts.length != 2) {
                                response = "Invalid command";
                                break;
                            }
                            String domain = parts[1];
                            if (domains.containsKey(domain)) {
                                response = "Domain already exists";
                                break;
                            }
                            domains.put(domain, new HashSet<>());
                            domainPermissions.put(domain, new HashSet<>());
                            domainPermissions.get(domain).add(userId);
                            response = "Domain created";
                            break;
                        case "ADD":
                            if (parts.length != 3) {
                                response = "Invalid command";
                                break;
                            }
                            String userToAdd = parts[1];
                            String domainToAdd = parts[2];
                            if (!hasDomainPermission(userId, domainToAdd)) {
                                response = "You do not have permission to add users to this domain";
                                break;
                            }
                            if (!users.containsKey(userToAdd)) {
                                response = "User does not exist";
                                break;
                            }
                            if (!addDomainPermission(userToAdd, domainToAdd)) {
                                response = "User already has permission for this domain";
                                break;
                            }
                            response = "User added to domain";
                            break;
                        case "RD":
                            if (parts.length != 2) {
                                response = "Invalid command";
                                break;
                            }
                            String domainToRegister = parts[1];
                            if (!hasDomainPermission(userId, domainToRegister)) {
                                response = "You do not have permission to register devices in this domain";
                                break;
                            }
                            if (!registerDevice(userId, deviceId, domainToRegister)) {
                                response = "Device already registered in this domain";
                                break;
                            }
                            response = "Device registered in domain";
                            break;
                        case "ET":
                            if (parts.length != 2) {
                                response = "Invalid command";
                                break;
                            }
                            float temperature = Float.parseFloat(parts[1]);
                            if (!registerTemperatureData(deviceId, temperature)) {
                                response = "Device not registered";
                                break;
                            }
                            response = "Temperature data registered";
                            break;
                        case "EI":
                            if (parts.length != 2) {
                                response = "Invalid command";
                                break;
                            }
                            byte[] image = (byte[]) in.readObject();
                            if (!registerImageData(deviceId, image)) {
                                response = "Device not registered";
                                break;
                            }
                            response = "Image data registered";
                            break;
                        case "RT":
                            if (parts.length != 2) {
                                response = "Invalid command";
                                break;
                            }
                            String domainToRead = parts[1];
                            if (!hasDomainPermission(userId, domainToRead)) {
                                response = "You do not have permission to read data from this domain";
                                break;
                            }

                            // Read temperature data from file
                            try (BufferedReader reader = new BufferedReader(
                                    new FileReader("data/temperature_" + domainToRead + ".txt"))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    String[] parts2 = line.split(",");
                                    String deviceId2 = parts2[0];
                                    float temperatureValue = Float.parseFloat(parts2[1]);
                                    if (temperatureValue != 0) {
                                        response += deviceId2 + ": " + temperatureValue + "\n";
                                    }
                                }
                                if (response.isEmpty()) {
                                    response = "No temperature data available for this domain";
                                }
                            } catch (IOException e) {
                                System.err.println("Error reading temperature data: " + e.getMessage());
                                response = "No temperature data available for this domain";
                            }
                            break;

                        case "RI":
                            if (parts.length != 2) {
                                response = "Invalid command";
                                break;
                            }
                            String[] deviceParts = parts[1].split(":");
                            String userToRead = deviceParts[0];
                            String deviceToRead = deviceParts[1];
                            if (!hasDomainPermission(userId, "default")
                                    || !hasDomainPermission(userToRead, "default")) {
                                response = "You do not have permission to read image data from this device";
                                break;
                            }

                            // Read image data from file
                            try (FileInputStream fis = new FileInputStream("data/image_" + deviceToRead + ".jpg")) {
                                byte[] imageDataValue = fis.readAllBytes();
                                if (imageDataValue != null) {
                                    out.writeObject(imageDataValue);
                                } else {
                                    response = "No image data available for this device";
                                }
                            } catch (IOException e) {
                                System.err.println("Error reading image data: " + e.getMessage());
                                response = "No image data available for this device";
                            }
                            break;

                        default:
                            response = "Invalid command";
                            break;
                    }

                    out.writeObject(response);
                }

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error handling client request: " + e.getMessage());
            }
        }
    }
}