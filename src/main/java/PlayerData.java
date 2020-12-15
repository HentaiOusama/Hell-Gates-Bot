import org.telegram.telegrambots.meta.api.objects.User;

public class PlayerData {
    int playerId;
    String addy;
    String username;
    boolean didPlayerPay = false;
    String uniqueDoorArrangement;
    int shotsToFire = 0;
    boolean didGotShot = false;


    @Override
    public int hashCode() {
        return playerId;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == this) {
            return true;
        } else if (obj instanceof String) {
            return obj.equals(addy);
        } else if (obj instanceof Integer) {
            return obj.equals(playerId);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "Id : " + playerId + "\nAddy : " + addy + "\ndidPlayerPay : " + didPlayerPay + "\nshotsToFire : " + shotsToFire;
    }
}
