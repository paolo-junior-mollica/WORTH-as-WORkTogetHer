import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

/**
 * Un oggetto WorkerTask rappresenta un task creato dal server, il cui compito 
 * è di soddisfare una richiesta del client. Al task viene passato il canale 
 * per la comunicazione con il client e un byte buffer, il quale contiene la
 * codifica in bytes del messaggio che il client ha inviato al server. Il messaggio 
 * contiene la richiesta del client e tutte le informazioni che servono al thread 
 * per soddisfarla.
 */
public class WorkerTask implements Runnable {

    /** Il messaggio che il client ha inviato al server */
    private final ClientServerMessage message;

    /** Il canale per la comunicazione con il client */
    private final SocketChannel client;

    /**
     * Costruttore: decodifica il contenuto del byte buffer per ricavare una stringa 
     * che corrisponde al messaggio serializzato, lo deserializza e salva il 
     * riferimento del messaggio nella variabile d'istanza this.message. Infine
     * salva il riferimento al canale nella variabile d'istanza this.client.
     * 
     * @param client     il canale per la comunicazione con il client
     * @param byteBuffer il buffer contenente la codifica in bytes del messaggio 
     *                   serializzato
     */
    public WorkerTask(SocketChannel client, ByteBuffer byteBuffer) {
        StringBuilder stringBuilder = new StringBuilder();
        while (byteBuffer.hasRemaining())
            stringBuilder.append(StandardCharsets.UTF_8.decode(byteBuffer).toString());
        Gson gson = new Gson();
        this.message = gson.fromJson(stringBuilder.toString(), ClientServerMessage.class);
        this.client = client;
    }

    @Override
    public void run() {
        /*
        Chiama un diverso metodo del server a seconda della richiesta del client. 
        I parametri che servono ai metodi sono anch'essi tutti contenuti nel messaggio.
        Tutti i metodi restituiscono un messaggio di risposta da inviare al client, 
        dove all'interno è specificato un valore della enum Replies (che rappresenta il 
        risultato dell'operazione) e altri campi a seconda della richiesta.
        Ricapitolando, a seconda del case chiama un diverso metodo ottenendo così un 
        messaggio di risposta, che infine invia al client.
         */
        switch (this.message.getComando()) {
            case LOGIN -> {
                ClientServerMessage replyMessage = ServerMain.login(this.message.getNickname(), this.message.getPassword());
                sendToClient(replyMessage);
            }

            case LOGOUT -> {
                ClientServerMessage replyMessage = ServerMain.logout(this.message.getUser());
                sendToClient(replyMessage);
                try {
                    // Chiusura del canale
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            case LIST_PROJECTS -> {
                ClientServerMessage replyMessage = ServerMain.listProjects(this.message.getNickname());
                sendToClient(replyMessage);
            }

            case CREATE_PROJECT -> {
                ClientServerMessage replyMessage = ServerMain.createProject(this.message.getNickname(),
                        this.message.getProjectName());
                sendToClient(replyMessage);
            }

            case ADD_MEMBER -> {
                ClientServerMessage replyMessage = ServerMain.addMember(this.message.getNickname(), this.message.getProjectName(),
                        this.message.getNewMember());
                sendToClient(replyMessage);
            }

            case SHOW_MEMBERS -> {
                ClientServerMessage replyMessage = ServerMain.showMembers(this.message.getNickname(), this.message.getProjectName());
                sendToClient(replyMessage);
            }

            case SHOW_CARDS -> {
                ClientServerMessage replyMessage = ServerMain.showCards(this.message.getNickname(), this.message.getProjectName());
                sendToClient(replyMessage);
            }

            case SHOW_CARD -> {
                ClientServerMessage replyMessage = ServerMain.showCard(this.message.getNickname(), this.message.getProjectName(), this.message.getCardName());
                sendToClient(replyMessage);
            }

            case ADD_CARD -> {
                ClientServerMessage replyMessage = ServerMain.addCard(this.message.getNickname(), this.message.getProjectName(),
                        this.message.getCardName(), this.message.getDescrizione());
                sendToClient(replyMessage);
            }

            case MOVE_CARD -> {
                ClientServerMessage replyMessage = ServerMain.moveCard(this.message.getNickname(), this.message.getProjectName(),
                        this.message.getCardName(), this.message.getListaPartenza(), this.message.getListaDestinazione());
                sendToClient(replyMessage);
            }

            case CANCEL_PROJECT -> {
                ClientServerMessage replyMessage = ServerMain.cancelProject(this.message.getNickname(), this.message.getProjectName());
                sendToClient(replyMessage);
            }

            default -> throw new IllegalArgumentException("Unexpected value: " + this.message.getComando());
        }
    }

    /**
     * Metodo per l'invio del messaggio di risposta al client. I messaggi sono 
     * oggetti di tipo ClientServerMessage che contengono tutte le informazioni 
     * che servono al client. Il messaggio viene serializzato, e vengono inviati al
     * client i bytes contenuti in due byte buffers: uno contenente i byte della 
     * stringa ottenuta con la serializzazione del messaggio, l'altro contenente il 
     * numero di bytes del precedente (il quale servirà al client per allocare un
     * byte buffer grande abbastanza per leggere tutto il messaggio).
     * 
     * @param message il messaggio di risposta per il client
     */
    private void sendToClient(ClientServerMessage message) {
        Gson gson = new Gson();
        // Serializzazione del messaggio
        String str = gson.toJson(message);
        // Codifica della stringa in un array di byte
        byte[] byteArray = str.getBytes(StandardCharsets.UTF_8);
        // Allocazione del byte buffer utilizzato per inviare il numero di bytes del messaggio serializzato
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
        // Inizializzazione con la lunghezza dell'array di byte
        sizeBuffer.putInt(byteArray.length);
        // Preparazione del buffer alla scrittura dopo la lettura
        sizeBuffer.flip();
        // Creazione del buffer contenente i bytes del messaggio serializzato
        ByteBuffer dataBuffer = ByteBuffer.wrap(byteArray);
        // Finché i bytes di entrambi i buffer non sono stati inviati continua
        while (sizeBuffer.hasRemaining() && dataBuffer.hasRemaining())
            try {
                client.write(new ByteBuffer[]{sizeBuffer, dataBuffer});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
}