import java.io.Serial;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.ArrayList;

/**
 * Implementazione dell'oggetto remoto esportato dal client
 */
public class NotifyEventImpl extends RemoteObject implements NotifyEventInterface {
    @Serial
    private static final long serialVersionUID = -9052908664508680394L;

    @Override
    public void notifyUsersEvent(ArrayList<User> registeredUsers) throws RemoteException {
        // Rimpiazzo della lista degli utenti registrati
        ClientMain.getUserLock().writeLock().lock();
        ClientMain.getUser().setUsersList(registeredUsers);
        ClientMain.getUserLock().writeLock().unlock();
    }

    @Override
    public void notifyChatsEvent(ArrayList<Project> createdProjects) throws RemoteException {
        // Lista di chat
        ArrayList<Chat> userChats = new ArrayList<>();
        // Per ogni progetto
        for (Project project : createdProjects) {
            // Se l'utente è membro del progetto
            if (project.getMembers().contains(ClientMain.getUser().getNickname()))
                // Aggiungo una chat alla lista appena creata
                userChats.add(new Chat(project.getChatAddress(), project.getChatPort(), project.getName()));
        }
        // Setto la lista di chat dell'utente con quella nuova. Per come è implementata 
        // la setChatsList(), non vengono rimpiazzate le chat dei progetti che erano già 
        // state create, quindi non ci sarà nessuna perdita di messaggi
        ClientMain.getUserLock().writeLock().lock();
        ClientMain.getUser().setChatsList(userChats);
        ClientMain.getUserLock().writeLock().unlock();
    }
}