import java.io.IOException;
import java.net.*;

/**
 * Implementazione di un task per la creazione di threads sniffer, il cui compito 
 * è quello di catturare i messaggi di una chat di un progetto.
 */
public class ChatSniffer implements Runnable {

    /** Il riferimento alla chat di cui deve "sniffare" i messaggi */
    private final Chat chat;

    /**
     * Costruttore
     * 
     * @param chat il riferimento alla chat
     */
    public ChatSniffer(Chat chat) {
        this.chat = chat;
    }

    @Override
    public void run() {
        try {
            // Creazione del multicast socket con la porta assegnata alla chat
            MulticastSocket multicastSocket = new MulticastSocket(chat.getPort());
            // Lego il multicast socket al gruppo multicast per la chat
            multicastSocket.joinGroup(new InetSocketAddress(chat.getAddress(), chat.getPort()), multicastSocket.getNetworkInterface());
            // Timeout per uscire dalla receive nel caso non arrivino messaggi (il progetto potrebbe essere stato
            // cancellato)
            multicastSocket.setSoTimeout(1000);
            // Finché l'utente è online e il thread non viene interrotto
            while (ClientMain.getUser().isOnline() && !Thread.currentThread().isInterrupted()) {
                try {
                    byte[] buffer = new byte[8192];
                    DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                    // Ricezione di un messaggio
                    multicastSocket.receive(datagramPacket);
                    // Decodifica del messaggio a stringa
                    String received = new String(datagramPacket.getData());
                    // Aggiunta del messaggio alla chat
                    ClientMain.getUserLock().writeLock().lock();
                    chat.getMessages().add(received.trim());
                    ClientMain.getUserLock().writeLock().unlock();
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}