import java.io.Serial;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;

/**
 * Implementazione dell'oggetto remoto esportato dal server
 */
public class WorthImpl extends RemoteServer implements WorthInterface {
    @Serial
    private static final long serialVersionUID = 5865078646221410458L;

    @Override
    public Replies register(String nickname, String password) throws RemoteException {
        // Creazione dell'utente
        User user = new User(nickname, password);
        // Controlla se è già registrato, e se non lo è lo aggiunge
        boolean wasAlreadyRegistered = ServerMain.putIfAbsent(user);
        // Se l'utente era già registrato
        if (wasAlreadyRegistered)
            return Replies.ALREADY_REGISTERED;
        // Altrimenti aggiorna le copie delle liste degli utenti registrati agli User
        ServerMain.updateAllUsersLists();
        // Ok
        return Replies.OK;
    }

    @Override
    public void registerForCallback(NotifyEventInterface clientStub) throws RemoteException {
        // Controlla se è già registrato, e se non lo è lo aggiunge
        boolean wasAlreadyRegistered = ServerMain.putIfAbsent(clientStub);
        // Se l'utente non era già registrato al servizio di callback
        if (!wasAlreadyRegistered) {
            // Aggiorna le liste degli utenti degli User
            ServerMain.updateAllUsersLists();
            // Aggiorna le liste delle chat degli User
            ServerMain.updateAllChatsLists();
        }
    }

    @Override
    public void unregisterForCallback(NotifyEventInterface clientStub) throws RemoteException {
        // Cancella la registrazione al servizio di callback
        ServerMain.removeStub(clientStub);
        // Aggiorna le liste degli utenti degli User
        ServerMain.updateAllUsersLists();
    }


}