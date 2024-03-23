import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class IoTDevice {

    private String serverAddress;
    private int serverPort;
    private int deviceId;
    private String userId;
    private String programName;
    private int programSize;

    public static void main(String[] args) {
        if (args.length < 3 || args.length > 4) {
            System.out.println("Invalid number of arguments");
            System.out.println("Usage: java IoTDevice <serverAddress> <serverPort> <deviceId> <userId>");
            return;
        }

        String serverAddress = args[0];
        int serverPort = args.length == 4 ? Integer.parseInt(args[1]) : 0;
        int deviceId = Integer.parseInt(args[args.length - 2]);
        String userId = args[args.length - 1];
        IoTDevice device = new IoTDevice(serverAddress, serverPort, deviceId, userId);
        device.start();
    }

    public IoTDevice(String serverAddress, int serverPort, int deviceId, String userId) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.deviceId = deviceId;
        this.userId = userId;
    }

    public void start() {
        try (Socket socket = new Socket(serverAddress, serverPort);
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                Scanner scanner = new Scanner(System.in)) {

            System.out.print("Enter password: ");
            String password = scanner.nextLine();

            out.writeObject(userId);
            out.writeObject(password);

            String response = (String) in.readObject();

            switch (response) {
                case "WRONG-PWD":
                    System.out.println("Invalid password");
                    return;
                case "OK-NEW-USER":
                    System.out.println("User registered");
                    break;
                case "OK-USER":
                    System.out.println("User authenticated");
                    break;
                default:
                    System.out.println("Unexpected response from server: " + response);
                    return;
            }

            out.writeObject(Integer.toString(deviceId));

            response = (String) in.readObject();

            while (response.equals("NOK-DEVID")) {
                System.out.print("Device ID already in use, enter new ID: ");
                deviceId = scanner.nextInt();
                out.writeObject(Integer.toString(deviceId));
                response = (String) in.readObject();
            }
            if (response.equals("OK-DEVID")) {
                System.out.println("Device ID registered");
            }

            out.writeObject((String) "IoTDevice.class");
            String filePath = IoTDevice.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            filePath = filePath + IoTDevice.class.getName().replace(".", "/") + ".class";
            System.out.println("Program file path: " + filePath);
            System.out.println("Program file size: " + filePath.length() + " bytes");
            out.writeObject(filePath.length());

            response = (String) in.readObject();

            if (response.equals("NOK-TESTED")) {
                System.out.println("Program not validated by server");
                return;
            }

            System.out.println("Program validated by server");

            while (true) {
                System.out.println();
                System.out.println("Available commands:");
                System.out.println("CREATE <dm>");
                System.out.println("ADD <user1> <dm>");
                System.out.println("RD <dm>");
                System.out.println("ET <float>");
                System.out.println("EI <filename.jpg>");
                System.out.println("RT <dm>");
                System.out.println("RI <user-id>:<dev_id>");
                System.out.print("Enter command: ");
                System.out.println();
                String command = scanner.nextLine();
                String[] parts = command.split(" ");
                String responseMessage = "";

                switch (parts[0]) {
                    case "CREATE":
                    case "ADD":
                    case "RD":
                    case "ET":
                        // Send command to server
                        out.writeObject(command);
                        // Receive response from server
                        responseMessage = (String) in.readObject();
                        break;
                    case "EI":
                        // Send command to server
                        out.writeObject(parts[0]);
                        // Read image file
                        File imageFile = new File(parts[1]);
                        byte[] imageData = new byte[(int) imageFile.length()];
                        try (FileInputStream fileInputStream = new FileInputStream(imageFile)) {
                            fileInputStream.read(imageData);
                        } catch (IOException e) {
                            System.err.println("Error reading image file: " + e.getMessage());
                            break;
                        }
                        // Send image data to server
                        out.writeObject(imageData);
                        // Receive response from server
                        responseMessage = (String) in.readObject();
                        break;
                    case "RT":
                    case "RI":
                        // Send command to server
                        out.writeObject(command);
                        // Receive response from server
                        responseMessage = (String) in.readObject();
                        // Print response to console
                        System.out.println(responseMessage);
                        break;
                    default:
                        System.out.println("Invalid command");
                        break;
                }

                if (!responseMessage.isEmpty()) {
                    System.out.println(responseMessage);
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error communicating with server: " + e.getMessage());
        }
    }
}