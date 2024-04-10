
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IoTServer {

    private int port;
    private Map<String, String> users; // map of user-id and password
    private Map<String, Set<String>> domains; // map of domain name (name) and associated devices (user-id:device-id)
    private Map<String, Map<String, String>> domainPermissions; // map of domain name and users and corresponding
    // permissions (read or owner)
    private Map<String, Float> temperatureData; // map of device-id and last temperature value
    private Map<String, String> imageData; // map of userId:device-id and last image data
    private ArrayList<String> onlineUsers; // Map of Online User:deviceId

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
        this.onlineUsers = new ArrayList<>();

        // Create data directory if it doesn't exist
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdir();
        }

        File imagesDir = new File("images");
        if (!imagesDir.exists()) {
            imagesDir.mkdir();
        }

        // Load user data from file or create file if it doesn't exist
        File usersFile = new File("data/users.txt");
        if (usersFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    users.put(parts[0], parts[1]);
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

        // Load domain data from file or create file if it doesn't exist
        File domainFile = new File("data/domains.txt");
        if (domainFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(domainFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    String domain = parts[0];
                    String[] devices = parts[1].split(",");
                    String[] users = parts[2].split(",");
                    String[] readUsers = parts[3].split(",");
                    String owner = parts[4];

                    // Add devices to domain
                    if (!domains.containsKey(domain)) {
                        domains.put(domain, new HashSet<>());
                    }
                    for (int i = 0; i < devices.length; i++) {
                        String deviceId = devices[i];
                        String userId = users[i];
                        domains.get(domain).add(userId + ":" + deviceId);
                    }

                    // Add users and permissions to domainPermissions
                    if (!domainPermissions.containsKey(domain)) {
                        domainPermissions.put(domain, new HashMap<>());
                    }
                    for (String user : readUsers) {
                        if (!domainPermissions.get(domain).containsKey(user)) {
                            domainPermissions.get(domain).put(user, "read");
                        }
                    }
                    domainPermissions.get(domain).put(owner, "owner");
                }
            } catch (IOException e) {
                System.err.println("Error loading device data: " + e.getMessage());
            }
        } else {
            try {
                domainFile.createNewFile();
            } catch (IOException e) {
                System.err.println("Error creating devices file: " + e.getMessage());
            }
        }

        File temperaturesFile = new File("data/temperatures.txt");
        if (!temperaturesFile.exists()) {
            try {
                temperaturesFile.createNewFile();
            } catch (IOException e) {
                System.err.println("Error creating temperatures file: " + e.getMessage());
            }
        } else {
            // Load temperature data from file
            try (BufferedReader reader = new BufferedReader(new FileReader(temperaturesFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    String userIdDeviceId = parts[0];
                    float temperature = Float.parseFloat(parts[1]);
                    temperatureData.put(userIdDeviceId, temperature);
                }
            } catch (IOException e) {
                System.err.println("Error loading temperature data: " + e.getMessage());
            }
        }

        File imageFile = new File("data/images.txt");
        if (!imageFile.exists()) {
            try {
                imageFile.createNewFile();
            } catch (IOException e) {
                System.err.println("Error creating images file: " + e.getMessage());
            }
        } else {
            // Load temperature data from file
            try (BufferedReader reader = new BufferedReader(new FileReader(imageFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    imageData.put(parts[0], parts[1]);
                }
            } catch (IOException e) {
                System.err.println("Error loading images data: " + e.getMessage());
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
            System.out.println("Domain does not exist");
            return false; // domain does not exist
        }
        Set<String> devices = domains.get(domain);
        if (devices.contains(userId + ":" + deviceId)) {
            return false; // device already registered in domain
        }

        // Check if user exists in domain
        boolean userExists = false;
        if (domainPermissions.containsKey(domain)) {
            Map<String, String> permissions = domainPermissions.get(domain);
            if (permissions.containsKey(userId)) {
                userExists = true;
            }
        }
        if (!userExists) {
            System.out.println("User does not exist in domain");
            return false; // user does not exist in domain
        }

        // Add device to domain
        devices.add(userId + ":" + deviceId);

        try {
            // Read the contents of the file into a map of domain names to lines
            Map<String, String> linesMap = new HashMap<>();
            List<String> lines = Files.readAllLines(Paths.get("data/domains.txt"));
            for (String line : lines) {
                String[] parts = line.split(":");
                linesMap.put(parts[0], line);
            }

            // Find the line with the domain name you want and modify it
            if (linesMap.containsKey(domain)) {
                String line = linesMap.get(domain);
                String[] parts = line.split(":");
                String firstPart = parts[1] + deviceId + ",";
                String secondPart = parts[2] + userId + ",";
                parts[1] = firstPart;
                parts[2] = secondPart;
                String modifiedLine = String.join(":", parts);
                linesMap.put(domain, modifiedLine);
            }

            // Write the modified map back to the file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("data/domains.txt"))) {
                for (String line : linesMap.values()) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            // handle the exception here, e.g. print an error message
            System.err.println("Error: " + e.getMessage());
        }
        return true;
    }

    private synchronized boolean addDomainPermission(String userId, String domain) {
        if (!domains.containsKey(domain)) {
            return false; // domain does not exist
        }
        domainPermissions.get(domain).put(userId, domain);
        try {
            // Read the contents of the file into a map of domain names to lines
            Map<String, String> linesMap = new HashMap<>();
            List<String> lines = Files.readAllLines(Paths.get("data/domains.txt"));
            for (String line : lines) {
                String[] parts = line.split(":");
                linesMap.put(parts[0], line);
            }

            // Find the line with the domain name you want and modify it
            if (linesMap.containsKey(domain)) {
                String line = linesMap.get(domain);
                String[] parts = line.split(":");
                String modifiedLine = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3] + userId + "," + ":"
                        + parts[4];
                linesMap.put(domain, modifiedLine);
            }

            // Write the modified map back to the file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("data/domains.txt"))) {
                for (String line : linesMap.values()) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            // handle the exception here, e.g. print an error message
            System.err.println("Error: " + e.getMessage());
        }
        return true;
    }

    private synchronized boolean domainExists(String userId, String domain) {
        if (!domains.containsKey(domain)) {
            return false; // domain does not exist
        }
        return true;
    }

    private synchronized boolean hasDomainPermission(String userId, String domain) {
        if (!domains.containsKey(domain)) {
            return false; // domain does not exist
        }

        if (domainPermissions.get(domain).containsKey(userId)) {
            return true;
        }
        return false;
    }

    private boolean hasDomainPermission2(String userId, String userToRead, String deviceId) {
        for (String domain : domains.keySet()) {
            if (domains.get(domain).contains(userToRead + ":" + deviceId)
                    && domainPermissions.get(domain).containsKey(userId)) {
                return true;
            }
        }
        return false;
    }

    private synchronized boolean registerTemperatureData(String userId, String deviceId, float temperature) {
        temperatureData.put(userId + ":" + deviceId, temperature);

        // Update temperature data in file
        File temperaturesFile = new File("data/temperatures.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(temperaturesFile))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            boolean found = false;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(userId + ":" + deviceId)) {
                    line = userId + ":" + deviceId + "," + temperature;
                    found = true;
                }
                stringBuilder.append(line).append("\n");
            }
            if (!found) {
                stringBuilder.append(userId).append(":").append(deviceId).append(",").append(temperature).append("\n");
            }
            FileWriter writer = new FileWriter(temperaturesFile);
            writer.write(stringBuilder.toString());
            writer.close();
        } catch (IOException e) {
            System.err.println("Error updating temperature data in file: " + e.getMessage());
            return false;
        }

        return true;
    }

    private synchronized boolean registerImageData(String userId, String deviceId, String fileName, byte[] image) {
        imageData.put(userId + ":" + deviceId, fileName);

        // Update temperature data in file
        File imagesFile = new File("data/images.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(imagesFile))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            boolean found = false;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(userId + ":" + deviceId)) {
                    line = userId + ":" + deviceId + "," + fileName;
                    found = true;
                }
                stringBuilder.append(line).append("\n");
            }
            if (!found) {
                stringBuilder.append(userId).append(":").append(deviceId).append(",").append(fileName).append("\n");
            }
            FileWriter writer = new FileWriter(imagesFile);
            writer.write(stringBuilder.toString());
            writer.close();
        } catch (IOException e) {
            System.err.println("Error updating temperature data in file: " + e.getMessage());
            return false;
        }

        // Create images folder if it doesn't exist
        File imagesFolder = new File("images");
        if (!imagesFolder.exists()) {
            if (!imagesFolder.mkdir()) {
                System.err.println("Error creating images folder");
                return false;
            }
        }

        // Save image to file
        File imageFile = new File(imagesFolder, fileName);
        try (FileOutputStream outputStream = new FileOutputStream(imageFile)) {
            outputStream.write(image);
        } catch (IOException e) {
            System.err.println("Error saving image to file: " + e.getMessage());
            return false;
        }

        return true;
    }

    private class ClientHandler extends Thread {

        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        String userId = null;
        String deviceId = null;

        @Override
        public void run() {

            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream());) {

                System.out.println("Client connected: " + socket.getInetAddress().getHostAddress());
                userId = (String) in.readObject();
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

                deviceId = (String) in.readObject();

                while (onlineUsers.contains(userId + ":" + deviceId)) {
                    out.writeObject("NOK-DEVID");
                    deviceId = (String) in.readObject();
                }

                out.writeObject("OK-DEVID");
                onlineUsers.add(userId + ":" + deviceId);

                String programName = (String) in.readObject();
                int programSize = (Integer) in.readObject();

                File file = new File("IoTDevice.class");
                if (file.exists() && file.isFile() && file.getName().equals(programName)
                        && file.length() == programSize) {
                    out.writeObject("OK-TESTED");
                } else {
                    out.writeObject("NOK-TESTED");
                }

                while (true) {
                    String command = (String) in.readObject();
                    String[] parts = command.split(" ");
                    String response = "";

                    switch (parts[0]) {
                        case "CREATE":
                            if (parts.length != 2) {
                                response = "NOK";
                                break;
                            }
                            String domain = parts[1];
                            if (domains.containsKey(domain)) {
                                response = "NOK";
                                break;
                            }
                            domains.put(domain, new HashSet<>());
                            domainPermissions.put(domain, new HashMap<String, String>());
                            domainPermissions.get(domain).put(userId, "owner");
                            try (BufferedWriter writer = new BufferedWriter(new FileWriter("data/domains.txt", true))) {
                                writer.write(domain + ":" + ":" + ":" + ":" + userId);
                                writer.newLine();
                            } catch (IOException e) {
                                System.err.println("Error writing domain data: " + e.getMessage());
                            }
                            response = "OK";
                            break;
                        case "ADD":
                            if (parts.length != 3) {
                                response = "NOK";
                                break;
                            }
                            String userToAdd = parts[1];
                            String domainToAdd = parts[2];
                            if (!domainExists(userId, domainToAdd)) {
                                response = "NODM";
                                break;
                            }
                            if (!hasDomainPermission(userId, domainToAdd)) {
                                response = "NOPERM";
                                break;
                            }
                            if (!users.containsKey(userToAdd)) {
                                response = "NOUSER";
                                break;
                            }
                            if (!addDomainPermission(userToAdd, domainToAdd)) {
                                response = "NODM";
                                break;
                            }
                            response = "OK";
                            break;
                        case "RD":
                            if (parts.length != 2) {
                                response = "NOK";
                                break;
                            }
                            String domainToRegister = parts[1];
                            if (!domainExists(userId, domainToRegister)) {
                                response = "NODM";
                                break;
                            }
                            if (!hasDomainPermission(userId, domainToRegister)) {
                                response = "NOPERM";
                                break;
                            }
                            if (!registerDevice(userId, deviceId, domainToRegister)) {
                                response = "NODM";
                                break;
                            }
                            response = "OK";
                            break;
                        case "ET":
                            if (parts.length != 2) {
                                response = "NOK";
                                break;
                            }
                            try {
                                Float.parseFloat(parts[1]);
                            } catch (NumberFormatException e) {
                                response = "NOK";
                                break;
                            }
                            float temperature = Float.parseFloat(parts[1]);
                            if (!registerTemperatureData(userId, deviceId, temperature)) {
                                response = "NOK";
                                break;
                            }
                            response = "OK";
                            break;
                        case "EI":
                            if (parts.length != 2) {
                                response = "NOK";
                                break;
                            }
                            if (!registerImageData(userId, deviceId, parts[1], (byte[]) in.readObject())) {
                                response = "NOK";
                                break;
                            }
                            response = "OK";
                            break;
                        case "RT":
                            if (parts.length != 2) {
                                response = "NOK";
                                break;
                            }
                            String domainToRead = parts[1];
                            if (!hasDomainPermission(userId, domainToRead)) {
                                response = "NOPERM";
                                break;
                            }
                            Set<String> devices = domains.get(domainToRead);
                            if (devices == null || devices.isEmpty()) {
                                response = "NODM";
                                break;
                            }
                            StringBuilder data = new StringBuilder();
                            for (String device : devices) {
                                Float temperatureTS = temperatureData.get(device);
                                if (temperatureTS != null) {
                                    data.append("Device: ").append(device);
                                    data.append("  Last temperature: " + temperatureTS).append("\n");
                                    data.append("\n");
                                }
                            }
                            if (data.length() == 0) {
                                response = "NODATA";
                                break;
                            }
                            response = "OK";
                            out.writeObject(response);
                            out.writeLong(data.length());
                            out.writeChars(data.toString());
                            break;

                        case "RI":
                            if (parts.length != 2) {
                                response = "NOK";
                                break;
                            }
                            String[] deviceParts = parts[1].split(":");
                            String userToRead = deviceParts[0];
                            String deviceToRead = deviceParts[1];
                            if (!hasDomainPermission2(userId, userToRead, deviceToRead)) {
                                response = "NOPERM";
                                break;
                            }

                            String fileName = imageData.get(userToRead + ":" + deviceToRead);
                            File imageFile = new File("images/" + fileName);
                            if (!imageFile.exists()) {
                                response = "NOID";
                                break;
                            }
                            byte[] imageData = Files.readAllBytes(imageFile.toPath());
                            response = "OK";
                            out.writeObject(response);
                            out.writeLong(imageData.length);
                            out.write(imageData);
                            break;
                        default:
                            response = "Invalid command";
                            break;
                    }

                    out.writeObject(response);
                }

            } catch (SocketException e) {
                System.out.println(userId + " has left");
                onlineUsers.remove(userId + ":" + deviceId);
            } catch (IOException | ClassNotFoundException e) {
                onlineUsers.remove(userId + ":" + deviceId);
            }
        }
    }
}