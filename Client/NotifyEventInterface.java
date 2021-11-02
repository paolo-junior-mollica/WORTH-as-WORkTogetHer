import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 * Interfaccia dell'oggetto remoto esportato dal client
 */
public interface NotifyEventInterface extends Remote {

    /**
     * Aggiorna la copia della lista degli utenti registrati, posseduta dall'utente 
     * che ha esportato l'oggetto.
     * 
     * @param registeredUsers la nuova copia della lista degli utenti registrati
     * @throws RemoteException metodo remoto
     */
    void notifyUsersEvent(ArrayList<User> registeredUsers) throws RemoteException;

    /**
     * Aggiorna la lista delle chat di tutti gli utenti
     * 
     * @param createdProjects la lista di tutti i progetti creati
     * @throws RemoteException metodo remoto
     */
    void notifyChatsEvent(ArrayList<Project> createdProjects) throws RemoteException;
}