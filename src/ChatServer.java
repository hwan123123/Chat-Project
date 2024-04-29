import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ChatServer {
    private static final Map<String, PrintWriter> clients = new HashMap<>();
    private static final Map<Integer, Set<String>> rooms = new HashMap<>();
    private static final Map<String, Integer> clientChatRooms = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("채팅 서버가 활성화되었습니다.");

            while (true) {
                Socket socket = serverSocket.accept();
                new ChatThread(socket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ChatThread extends Thread {
        private final Socket socket;
        private String id;
        private BufferedReader in;
        private PrintWriter out;

        public ChatThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                id = in.readLine().trim();

                synchronized (clients) {
                    while (clients.containsKey(id)) {
                        out.println("오류 : 닉네임이 중복되었습니다. 다른 닉네임을 입력해주세요.");
                        id = in.readLine().trim();
                    }
                    clients.put(id, out);
                }

                System.out.println(id + " 닉네임의 사용자가 연결되었습니다.");
                broadcast(id + " 닉네임의 사용자가 입장했습니다.");
                System.out.println(id + "'s IpAddress : " + socket.getInetAddress());

                commandList();

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equals("/bye")) {
                        System.out.println(id + " 닉네임의 사용자가 연결을 끊었습니다.");
                        break;
                    } else if (message.startsWith("/whisper")) {
                        whisperMsg(message);
                    } else if (message.equals("/list")) {
                        listRooms();
                    } else if (message.equals("/create")) {
                        createRoom();
                    } else if (message.startsWith("/join ")) {
                        joinRoom(message);
                    } else if (message.equals("/exit")) {
                        exitRoom();
                    } else if (message.equals("/users")) {
                        listUsers();
                    } else if (message.equals("/roomusers")) {
                        listRoomUsers();
                    } else {
                        broadcast(id + " : " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                synchronized (clients) {
                    clients.remove(id);
                }
                broadcast(id + "님이 채팅에서 나갔습니다.");

                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void listRooms() {
            out.println("현재 운영 중인 채팅방 목록");
            for (int roomId : rooms.keySet()) {
                out.println(roomId + "번 채팅방");
            }
        }

        private void createRoom() {
            synchronized (rooms) {
                Integer roomId = clientChatRooms.get(id);
                if (roomId == null) {
                    int createRoomId = 1;
                    while (rooms.containsKey(createRoomId)) {
                        createRoomId++;
                    }
                    rooms.put(createRoomId, new HashSet<>());
                    rooms.get(createRoomId).add(id);
                    clientChatRooms.put(id, createRoomId);
                    System.out.println(createRoomId + "번 방이 생성되었습니다.");
                    out.println(createRoomId + "번 방에 입장하였습니다.");
                } else {
                    out.println("이미 방에 입장해 있기 때문에 방을 생성할 수 없습니다.");
                }
            }
        }

        private void joinRoom(String message) {
            synchronized (rooms) {
                Integer roomId = clientChatRooms.get(id);
                if (roomId == null) {
                    String[] parts = message.split("\\s+");
                    try {
                        roomId = Integer.parseInt(parts[1]);
                        if (rooms.containsKey(roomId)) {
                            rooms.get(roomId).add(id);
                            clientChatRooms.put(id, roomId);
                            out.println(roomId + "번 방에 입장하였습니다.");
                            Set<String> roomMembers = rooms.get(roomId);
                            for (String memberId : roomMembers) {
                                if (!memberId.equals(id)) {
                                    PrintWriter pw = clients.get(memberId);
                                    if (pw != null) {
                                        pw.println(id + "님이 방에 입장하였습니다.");
                                    }
                                }
                            }
                        } else {
                            out.println(roomId + "번 방을 찾을 수 없습니다.");
                        }
                    } catch (NumberFormatException e) {
                        out.println("방 번호를 확인 후 다시 입력해주세요.");
                    }
                } else {
                    out.println("=================================================================");
                    out.println("채팅방에 입장하신 상태에서는 다른 채팅방으로 입장이 불가능합니다.\n현재 계신 채팅방에서 퇴장 후 다른 채팅방으로 입장해주세요."); // 이미 방에 입장한 경우에 대한 처리
                    out.println("=================================================================");
                }
            }
        }

        private void exitRoom() {
            Integer roomId = clientChatRooms.get(id);
            if (roomId != null) {
                Set<String> roomMembers = rooms.get(roomId);
                roomMembers.remove(id);
                clientChatRooms.remove(id);
                out.println(roomId + "번 방에서 퇴장하였습니다.");
                for (String memberId : roomMembers) {
                    PrintWriter pw = clients.get(memberId);
                    if (pw != null) {
                        pw.println(id + "님이 방에서 퇴장하였습니다.");
                    }
                }
                if (roomMembers.isEmpty()) {
                    rooms.remove(roomId);
                    System.out.println(roomId + "번 방이 삭제되었습니다.");
                }
            }
        }

        public void whisperMsg(String msg) {
            int firstSpaceIndex = msg.indexOf(" ");
            if (firstSpaceIndex == -1) {
                return;
            }

            int secondSpaceIndex = msg.indexOf(" ", firstSpaceIndex + 1);
            if (secondSpaceIndex == -1) {
                return;
            }

            String whisper = msg.substring(firstSpaceIndex + 1, secondSpaceIndex);
            String message = msg.substring(secondSpaceIndex + 1);

            PrintWriter pw = clients.get(whisper);
            if (pw != null) {
                pw.println(id + "님에게 온 귓속말 : " + message);
            } else {
                System.out.println("귓속말 오류 : " + whisper + "님을 찾을 수 없습니다.");
            }
        }

        private void listUsers() {
            out.println("현재 접속 중인 사용자 목록");
            for (String userId : clients.keySet()) {
                out.println(userId);
            }
        }

        private void listRoomUsers() {
            Integer roomId = clientChatRooms.get(id);
            if (roomId != null) {
                out.println("현재 " + roomId + "번 채팅방에 있는 사용자 목록");
                Set<String> roomMembers = rooms.get(roomId);
                for (String userId : roomMembers) {
                    out.println(userId);
                }
            } else {
                out.println("현재 채팅방에 속해있지 않습니다.");
            }
        }

        public void broadcast(String msg) {
            synchronized (clients) {
                if (clientChatRooms.containsKey(id)) {
                    int roomId = clientChatRooms.get(id);
                    Set<String> roomMembers = rooms.get(roomId);
                    for (String memberId : roomMembers) {
                        PrintWriter pw = clients.get(memberId);
                        if (pw != null) {
                            try {
                                pw.println(msg);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }

        private void commandList() {
            out.println("==============================================");
            out.println("방 목록 보기 : /list");
            out.println("방 생성 : /create");
            out.println("방 입장 : /join [방번호]");
            out.println("방 나가기 : /exit");
            out.println("접속 중인 사용자 목록 보기 : /users");
            out.println("현재 방에 있는 사용자 목록 보기 : /roomusers");
            out.println("접속 종료 : /bye");
            out.println("==============================================");
        }
    }
}
