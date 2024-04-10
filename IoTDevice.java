import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class IoTDevice {

    private String serverAddress;
    private int serverPort;
    private int deviceId;
    private String userId;

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
                    System.out.println();
                    return;
                case "OK-NEW-USER":
                    System.out.println("User registered");
                    System.out.println();
                    break;
                case "OK-USER":
                    System.out.println("User authenticated");
                    System.out.println();
                    break;
                default:
                    System.out.println("Unexpected response from server: " + response);
                    System.out.println();
                    return;
            }
            File directory = new File("clientImages");
            if (!directory.exists()) {
                directory.mkdir();
            }
            out.writeObject(Integer.toString(deviceId));

            response = (String) in.readObject();

            while (response.equals("NOK-DEVID")) {
                System.out.println();
                System.out.print("UserID:Device ID already in use, enter new ID: ");
                deviceId = scanner.nextInt();
                scanner.nextLine(); // consume newline character
                out.writeObject(Integer.toString(deviceId));
                response = (String) in.readObject();
            }
            if (response.equals("OK-DEVID")) {
                System.out.println("Device ID registered");
            }

            out.writeObject((String) "IoTDevice.class");
            String filePath = IoTDevice.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            filePath = filePath + IoTDevice.class.getName().replace(".", "/") + ".class";

            File file = new File(filePath);
            int fileSize = (int) file.length();
            out.writeObject(fileSize);

            response = (String) in.readObject();

            if (response.equals("NOK-TESTED")) {
                System.out.println();
                System.out.println("Program not validated by server");
                return;
            }
            System.out.println();
            System.out.println("Program validated by server");

            System.out.println();
            System.out.println("----- IoT Device started -----");
            System.out.println();
            System.out.println("Available commands:");
            System.out.println();
            System.out.println("CREATE <dm>");
            System.out.println("ADD <user1> <dm>");
            System.out.println("RD <dm>");
            System.out.println("ET <float>");
            System.out.println("EI <filename.jpg>");
            System.out.println("RT <dm>");
            System.out.println("RI <user-id>:<dev_id>");

            while (true) {

                System.out.println();
                System.out.print("Enter command: ");
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
                        if (parts.length != 2) {
                            responseMessage = "Invalid command";
                            break;
                        }
                        String fileName = parts[1];

                        File imageFile = new File("clientImages/" + fileName);
                        if (!imageFile.exists()) {
                            responseMessage = "File not found";
                            break;
                        }
                        byte[] imageData = Files.readAllBytes(imageFile.toPath());
                        out.writeObject(command);
                        out.writeObject(imageData);
                        responseMessage = (String) in.readObject();
                        break;

                    case "RT":
                        if (parts.length != 2) {
                            responseMessage = "Invalid command";
                            break;
                        }
                        out.writeObject(command);
                        response = (String) in.readObject();
                        if (!response.startsWith("OK")) {
                            System.out.println(response);
                            break;
                        }
                        System.out.println(response);
                        long dataSize = in.readLong();
                        StringBuilder data = new StringBuilder();
                        while (data.length() < dataSize) {
                            data.append(in.readChar());
                        }

                        try (BufferedWriter writer = new BufferedWriter(new FileWriter("temperature_data.txt", true))) {
                            writer.write(data.toString());
                        } catch (IOException e) {
                            System.err.println("Error writing temperature data: " + e.getMessage());
                        }
                        break;
                    case "RI":
                        if (parts.length != 2) {
                            responseMessage = "Invalid command";
                            break;
                        }
                        out.writeObject(command);
                        response = (String) in.readObject();
                        if (!response.startsWith("OK")) {
                            responseMessage = "OK";
                        }
                        long imageSize = in.readLong();

                        byte[] iData = new byte[(int) imageSize];
                        in.readFully(iData);

                        // Save byte array as image file
                        Path receivedImagesPath = Paths.get("receivedImages");
                        if (!Files.exists(receivedImagesPath)) {
                            Files.createDirectory(receivedImagesPath);
                        }
                        Path imagePath = receivedImagesPath.resolve("image.jpg");
                        Files.write(imagePath, iData);
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
        } catch (NoSuchElementException e) {
            // handle CTRL+C
            System.out.println("Program terminated by user.");
            System.exit(0);
        }
    }
}